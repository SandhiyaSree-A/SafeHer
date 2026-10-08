"""
sos_hotspot_service.py
Reads aggregated SOS hotspot data from Supabase Postgres (PostGIS) or Firestore
and scores proximity to known hotspots as a 0-100 safety factor.

Design contract:
  calculate_sos_hotspot_score(lat, lng, hour=None) -> (score|None, reason|None)
  Returns (None, reason) if DB unreachable - caller MUST skip the factor.

Privacy: reads only sos_hotspots (aggregated, anonymised). Never touches sos_alerts.
Ethical disclaimer: Risk-awareness estimate for prototype/demo purposes only,
not a guarantee of real-world safety or crime prediction.
"""
import os
import math
import time
import logging
import threading
from datetime import datetime, timezone
from typing import Optional, Tuple, List, Dict, Any

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configurable constants
# ---------------------------------------------------------------------------
MIN_ALERT_COUNT   = 3       # minimum weighted alerts for a cell to be a hotspot
MIN_UNIQUE_USERS  = 2       # minimum distinct users
SEARCH_RADIUS_M   = 200.0   # metres radius to consider nearby hotspots
CACHE_TTL_SECONDS = 60      # seconds before cache refresh
NIGHT_HOUR_START  = 22      # 22:00 late-night window start
NIGHT_HOUR_END    = 5       # 05:00 late-night window end

# Time-decay weights for alert age buckets
_DECAY_RECENT  = 1.0   # <= 90 days
_DECAY_OLD     = 0.5   # 90-180 days
_DECAY_ANCIENT = 0.25  # > 180 days

# Night-time multiplier when hotspot has many nightCount alerts
_NIGHT_EXTRA_WEIGHT = 1.4

SOS_SOURCE = os.environ.get("SOS_SOURCE", "postgres").lower()
DATABASE_URL = os.environ.get("DATABASE_URL")

# ---------------------------------------------------------------------------
# Module-level in-memory cache (thread-safe)
# ---------------------------------------------------------------------------
_cache_lock = threading.Lock()
_hotspots_cache: List[Dict[str, Any]] = []
_cache_loaded_at: float = 0.0
_init_failed = False
_pg_pool = None


def _get_pg_pool():
    global _pg_pool
    if _pg_pool is None and DATABASE_URL:
        try:
            from psycopg_pool import ConnectionPool
            _pg_pool = ConnectionPool(DATABASE_URL, min_size=1, max_size=5)
        except Exception as e:
            logger.warning("Failed to init psycopg pool: %s", e)
    return _pg_pool


def _firebase_app():
    """Lazily initialise firebase_admin; returns Firestore client or None."""
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore as fs_admin
        if not firebase_admin._apps:
            sa_path = os.environ.get(
                "GOOGLE_APPLICATION_CREDENTIALS",
                os.path.join(os.path.dirname(__file__), "..", "..", "serviceAccountKey.json")
            )
            if os.path.exists(sa_path):
                cred = credentials.Certificate(sa_path)
                firebase_admin.initialize_app(cred)
            else:
                firebase_admin.initialize_app()
        return fs_admin.client()
    except Exception as e:
        logger.warning("firebase_admin init failed: %s", e)
        return None


def _haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Returns distance in metres between two WGS-84 coordinates."""
    R = 6_371_000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi    = math.radians(lat2 - lat1)
    dlambda = math.radians(lng2 - lng1)
    a = (math.sin(dphi / 2) ** 2
         + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2)
    return 2 * R * math.asin(math.sqrt(a))


def _process_hotspot_row(cid: str, lat: float, lng: float, alert_count: int, unique_users: int, night_count: int, last_alert_at_ms: int, now_ms: int) -> Optional[Dict[str, Any]]:
    ninety_days_ms   = 90  * 24 * 3600 * 1000
    oneighty_days_ms = 180 * 24 * 3600 * 1000
    age_ms = now_ms - last_alert_at_ms
    if age_ms <= ninety_days_ms:
        decay = _DECAY_RECENT
    elif age_ms <= oneighty_days_ms:
        decay = _DECAY_OLD
    else:
        decay = _DECAY_ANCIENT
    weighted_count = (alert_count * _DECAY_RECENT) if age_ms <= ninety_days_ms else (alert_count * decay) # Simplification, to be robust if recent_count_90d is missing
    # To be precise as before if recent_count isn't available, we just apply decay on all. 
    # But since we have triggered_at, the view just gives last_alert_at. 
    # For now, apply decay based on last_alert_at to all alerts for that hotspot.
    if weighted_count < MIN_ALERT_COUNT or unique_users < MIN_UNIQUE_USERS:
        return None
    return {
        "id":             cid,
        "lat":            lat,
        "lng":            lng,
        "alert_count":    alert_count,
        "weighted_count": weighted_count,
        "unique_users":   unique_users,
        "night_count":    night_count,
    }


def _load_hotspots_postgres() -> bool:
    global _hotspots_cache, _cache_loaded_at, _init_failed
    pool = _get_pg_pool()
    if not pool:
        _init_failed = True
        return False
    try:
        now_ms = int(time.time() * 1000)
        hotspots = []
        with pool.connection() as conn:
            with conn.cursor() as cur:
                cur.execute("""
                    SELECT cid, ST_Y(center::geometry) as lat, ST_X(center::geometry) as lng, 
                           alert_count, unique_users, night_count, 
                           EXTRACT(EPOCH FROM last_alert_at)*1000 AS last_alert_at_ms
                    FROM sos_hotspots
                """)
                for row in cur:
                    cid, lat, lng, alert_count, unique_users, night_count, last_alert_at_ms = row
                    hs = _process_hotspot_row(cid, lat, lng, alert_count, unique_users, night_count, last_alert_at_ms, now_ms)
                    if hs:
                        hotspots.append(hs)
        with _cache_lock:
            _hotspots_cache  = hotspots
            _cache_loaded_at = time.time()
        logger.info("SOS hotspot cache refreshed (Postgres): %d qualifying hotspots", len(hotspots))
        return True
    except Exception as e:
        logger.warning("Failed to load sos_hotspots from Postgres: %s", e)
        return False


def _load_hotspots_firestore() -> bool:
    """Fetch sos_hotspots from Firestore into cache."""
    global _hotspots_cache, _cache_loaded_at, _init_failed
    db = _firebase_app()
    if db is None:
        _init_failed = True
        return False
    try:
        docs = db.collection("sos_hotspots").stream()
        hotspots = []
        now_ms           = int(time.time() * 1000)
        ninety_days_ms   = 90  * 24 * 3600 * 1000
        oneighty_days_ms = 180 * 24 * 3600 * 1000
        for doc in docs:
            d = doc.to_dict()
            if d.get("isFalseAlarm", False):
                continue
            alert_count   = d.get("alertCount", 0)
            recent_count  = d.get("recentCount_90d", 0)
            last_alert_at = d.get("lastAlertAt", 0)
            unique_users  = d.get("uniqueUserCount", 0)
            night_count   = d.get("nightCount", 0)
            age_ms = now_ms - last_alert_at
            if age_ms <= ninety_days_ms:
                decay = _DECAY_RECENT
            elif age_ms <= oneighty_days_ms:
                decay = _DECAY_OLD
            else:
                decay = _DECAY_ANCIENT
            weighted_count = (recent_count * _DECAY_RECENT
                              + max(0, alert_count - recent_count) * decay)
            if weighted_count < MIN_ALERT_COUNT or unique_users < MIN_UNIQUE_USERS:
                continue
            hotspots.append({
                "id":             doc.id,
                "lat":            d.get("centerLat", 0.0),
                "lng":            d.get("centerLng", 0.0),
                "alert_count":    alert_count,
                "weighted_count": weighted_count,
                "unique_users":   unique_users,
                "night_count":    night_count,
            })
        with _cache_lock:
            _hotspots_cache  = hotspots
            _cache_loaded_at = time.time()
        logger.info("SOS hotspot cache refreshed (Firestore): %d qualifying hotspots", len(hotspots))
        return True
    except Exception as e:
        logger.warning("Failed to load sos_hotspots from Firestore: %s", e)
        return False


def _load_hotspots() -> bool:
    if _init_failed:
        return False
    if SOS_SOURCE == "postgres":
        return _load_hotspots_postgres()
    else:
        return _load_hotspots_firestore()


def _get_cached_hotspots() -> Optional[List[Dict[str, Any]]]:
    """Return cached list, refreshing if TTL expired. Returns None if unavailable."""
    with _cache_lock:
        cache_valid = (
            (time.time() - _cache_loaded_at) < CACHE_TTL_SECONDS
            and _cache_loaded_at > 0
        )
    if cache_valid:
        with _cache_lock:
            return list(_hotspots_cache)
    success = _load_hotspots()
    if not success and _cache_loaded_at == 0:
        return None
    with _cache_lock:
        return list(_hotspots_cache)


def _is_late_night(hour: int) -> bool:
    """True when hour falls in the 22:00-05:00 late-night window."""
    if NIGHT_HOUR_START > NIGHT_HOUR_END:   # wraps midnight
        return hour >= NIGHT_HOUR_START or hour <= NIGHT_HOUR_END
    return NIGHT_HOUR_START <= hour <= NIGHT_HOUR_END


def calculate_sos_hotspot_score(
    lat: float,
    lng: float,
    hour: Optional[int] = None,
) -> Tuple[Optional[float], Optional[str]]:
    """
    Compute a 0-100 safety score based on nearby SOS hotspots.
    100 = no nearby hotspot (safest); lower = more/closer hotspots within 200 m.
    Returns (None, reason) when data unavailable - caller must skip this factor.
    """
    if hour is None:
        hour = datetime.now(timezone.utc).hour

    hotspots = _get_cached_hotspots()
    if hotspots is None:
        return None, f"SOS hotspot data unavailable ({SOS_SOURCE} unreachable)"
    if not hotspots:
        return 100.0, None   # no hotspots in dataset -> max score

    late_night = _is_late_night(hour)

    nearby: List[Dict[str, Any]] = []
    for hs in hotspots:
        dist = _haversine_m(lat, lng, hs["lat"], hs["lng"])
        if dist <= SEARCH_RADIUS_M:
            nearby.append({**hs, "distance_m": dist})

    if not nearby:
        return 100.0, None   # nothing close by

    total_penalty = 0.0
    for hs in nearby:
        proximity_weight = 1.0 - (hs["distance_m"] / SEARCH_RADIUS_M)
        intensity = float(hs["weighted_count"])
        night_ratio = hs["night_count"] / max(1, hs["alert_count"])
        if late_night and night_ratio >= 0.4:
            intensity *= _NIGHT_EXTRA_WEIGHT
        total_penalty += intensity * proximity_weight

    MAX_PENALTY = 5.0
    score = max(0.0, 100.0 * (1.0 - min(total_penalty, MAX_PENALTY) / MAX_PENALTY))
    return round(score, 2), None


def get_hotspots_near_route(
    sampled_points: List[Dict[str, Any]],
    radius_m: float = SEARCH_RADIUS_M,
) -> Tuple[int, List[Dict[str, Any]]]:
    """
    Return (count, list) of unique hotspots within radius_m of any sampled route point.
    Each item: {lat, lng, alert_count}.
    """
    hotspots = _get_cached_hotspots()
    if not hotspots:
        return 0, []
    seen_ids: set = set()
    result: List[Dict[str, Any]] = []
    for pt in sampled_points:
        for hs in hotspots:
            if hs["id"] in seen_ids:
                continue
            dist = _haversine_m(pt["latitude"], pt["longitude"], hs["lat"], hs["lng"])
            if dist <= radius_m:
                seen_ids.add(hs["id"])
                result.append({
                    "lat":         hs["lat"],
                    "lng":         hs["lng"],
                    "alert_count": hs["alert_count"],
                })
    return len(result), result
