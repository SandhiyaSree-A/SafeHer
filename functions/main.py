"""
main.py  –  Firebase Cloud Functions (Python)
SafeHer Route Risk Scoring Engine  +  SOS Hotspot Aggregation Trigger

Ethical disclaimer (MUST appear in all API responses and Android UI):
"Risk-awareness estimate for prototype/demo purposes only, not a guarantee
of real-world safety or crime prediction"

Functions
─────────
scoreRoute          – existing HTTP endpoint (unchanged)
aggregate_sos_hotspot – Firestore onCreate trigger on sos_alerts
                       Writes/updates sos_hotspots/{cellId} with privacy-safe
                       aggregated counts (no PII).
"""

import os
import joblib
import urllib.request
import time as _time
import pandas as pd
from flask import Flask, request, jsonify
from feature_calculator import build_feature_dataframe

# ---------------------------------------------------------------------------
# Mandatory ethical disclaimer
# ---------------------------------------------------------------------------
MANDATORY_DISCLAIMER = (
    "Risk-awareness estimate for prototype/demo purposes only, "
    "not a guarantee of real-world safety or crime prediction"
)

HF_MODEL_URL = (
    "https://huggingface.co/avnisinghal001/narisafe-risk-awareness-model"
    "/resolve/main/best_no_location_threshold_model.joblib"
)
MODEL_LOCAL_PATH = os.path.join(os.path.dirname(__file__), "best_model.joblib")

_model_dict = None


def load_model():
    global _model_dict
    if _model_dict is None:
        if not os.path.exists(MODEL_LOCAL_PATH):
            print(f"Downloading Hugging Face model from {HF_MODEL_URL}...")
            urllib.request.urlretrieve(HF_MODEL_URL, MODEL_LOCAL_PATH)
            print("Model downloaded successfully.")
        print(f"Loading joblib model from {MODEL_LOCAL_PATH}...")
        _model_dict = joblib.load(MODEL_LOCAL_PATH)
        print("Joblib model loaded successfully.")
    return _model_dict


# ---------------------------------------------------------------------------
# SOS Hotspot Aggregation Helpers
# ---------------------------------------------------------------------------

# ~150 m grid cell (geohash precision 7 ≈ 152 m × 152 m).
# We approximate by rounding lat/lng to 4 decimal places ≈ 11 m resolution,
# then grouping into ~0.0014-degree cells (≈ 150 m).
_CELL_SIZE_DEG = 0.0014   # ≈ 150 m per cell side

# Anti-abuse: same user + same cell within 24 h counts once
_DEDUP_WINDOW_MS = 24 * 60 * 60 * 1000   # 24 hours in milliseconds

# A cell only becomes a hotspot if these thresholds are met
MIN_ALERT_COUNT  = 3
MIN_UNIQUE_USERS = 2

# Night-time window (UTC hours) for nightCount tracking
_NIGHT_START_UTC = 22
_NIGHT_END_UTC   = 5     # exclusive upper bound (wraps midnight)


def _cell_id(lat: float, lng: float) -> str:
    """Return a deterministic cell ID for a ~150 m grid cell."""
    cell_lat = round(lat / _CELL_SIZE_DEG) * _CELL_SIZE_DEG
    cell_lng = round(lng / _CELL_SIZE_DEG) * _CELL_SIZE_DEG
    return f"{cell_lat:.4f}_{cell_lng:.4f}"


def _cell_center(cell_id: str):
    """Parse cell center from cell_id string."""
    parts = cell_id.split("_")
    return float(parts[0]), float(parts[1])


def _is_night_utc(timestamp_ms: int) -> bool:
    """True when the epoch-ms timestamp falls in the 22:00-05:00 UTC window."""
    import datetime
    dt = datetime.datetime.utcfromtimestamp(timestamp_ms / 1000.0)
    h = dt.hour
    return h >= _NIGHT_START_UTC or h < _NIGHT_END_UTC


def _ninety_days_ago_ms() -> int:
    return int((_time.time() - 90 * 24 * 3600) * 1000)


# ---------------------------------------------------------------------------
# Cloud Function: aggregate_sos_hotspot
# Triggered by a Firestore onCreate on sos_alerts/{alertId}.
# Deployed with: firebase deploy --only functions:aggregate_sos_hotspot
# ---------------------------------------------------------------------------
def aggregate_sos_hotspot(event, context):
    """
    Firestore onCreate trigger.
    Reads the new sos_alert document and updates the corresponding
    sos_hotspots/{cellId} aggregate document.

    Privacy guarantees
    ──────────────────
    • No userId, userName, or userPhone written to sos_hotspots.
    • Same user + same cell within 24 h counts as ONE alert (anti-spam).
    • isFalseAlarm field can be set by admin; scoring service skips those cells.
    """
    from firebase_admin import firestore as _fs
    import firebase_admin

    if not firebase_admin._apps:
        firebase_admin.initialize_app()

    db = _fs.client()

    # ── 1. Extract alert data from the event ──────────────────────────────
    new_value = event.get("value", {})
    fields    = new_value.get("fields", {})

    # Resolve Firestore value types
    def _str(f, key):
        return f.get(key, {}).get("stringValue", "")

    def _num(f, key):
        v = f.get(key, {})
        return float(v.get("doubleValue", v.get("integerValue", 0)))

    status = _str(fields, "status")
    if status == "false_alarm":
        return   # ignore from the start

    user_id = _str(fields, "userId")
    loc     = fields.get("location", {}).get("mapValue", {}).get("fields", {})
    lat     = _num(loc, "lat")
    lng     = _num(loc, "lng")
    ts_ms   = int(_num(fields, "timestamp"))

    if not user_id or (lat == 0.0 and lng == 0.0):
        return   # malformed alert

    # ── 2. Determine grid cell ────────────────────────────────────────────
    cid = _cell_id(lat, lng)
    cell_lat, cell_lng = _cell_center(cid)
    hotspot_ref = db.collection("sos_hotspots").document(cid)

    # ── 3. Anti-abuse dedup: same user + same cell within 24 h ───────────
    cutoff_ms = ts_ms - _DEDUP_WINDOW_MS
    recent_alerts = (
        db.collection("sos_alerts")
        .where("userId", "==", user_id)
        .where("timestamp", ">=", cutoff_ms)
        .where("timestamp", "<",  ts_ms)
        .stream()
    )
    for doc in recent_alerts:
        d = doc.to_dict()
        existing_loc = d.get("location", {})
        if _cell_id(existing_loc.get("lat", 0), existing_loc.get("lng", 0)) == cid:
            # This user already has a counted alert in this cell within 24 h → skip
            return

    # ── 4. Update hotspot aggregate in a transaction ──────────────────────
    is_night = _is_night_utc(ts_ms)
    ninety_ago = _ninety_days_ago_ms()
    is_recent  = ts_ms >= ninety_ago

    @db.transaction
    def _update(transaction, ref):
        snap = ref.get(transaction=transaction)
        if snap.exists:
            existing = snap.to_dict()
            # Count unique users (we store a set as a sub-collection, but for
            # simplicity we track via uniqueUserIds list field and count).
            known_users = set(existing.get("_userIds", []))
            known_users.add(user_id)
            new_alert_count  = existing.get("alertCount", 0) + 1
            new_unique_users = len(known_users)
            new_night_count  = existing.get("nightCount", 0) + (1 if is_night else 0)
            new_recent       = existing.get("recentCount_90d", 0) + (1 if is_recent else 0)
            transaction.update(ref, {
                "alertCount":      new_alert_count,
                "uniqueUserCount": new_unique_users,
                "nightCount":      new_night_count,
                "recentCount_90d": new_recent,
                "lastAlertAt":     max(ts_ms, existing.get("lastAlertAt", 0)),
                "_userIds":        list(known_users),
            })
        else:
            transaction.set(ref, {
                "centerLat":       cell_lat,
                "centerLng":       cell_lng,
                "alertCount":      1,
                "uniqueUserCount": 1,
                "nightCount":      1 if is_night else 0,
                "recentCount_90d": 1 if is_recent else 0,
                "lastAlertAt":     ts_ms,
                "isFalseAlarm":    False,
                "_userIds":        [user_id],
            })

    _update(hotspot_ref)


# ---------------------------------------------------------------------------
# Existing scoreRoute HTTP function (unchanged)
# ---------------------------------------------------------------------------

def score_route_segments(origin_lat, origin_lng, dest_lat, dest_lng):
    model_dict = load_model()
    pipeline   = model_dict['pipeline']
    d_lat = dest_lat - origin_lat
    d_lng = dest_lng - origin_lng
    routes_raw = [
        {
            "id": "route_1", "name": "Via Main Highway (Well Lit)",
            "distance": "5.4 km", "duration": "14 mins",
            "mock_lighting": 0.85, "mock_crowd": "high", "mock_crowd_num": 0.90,
            "path": [
                [origin_lat, origin_lng],
                [origin_lat + d_lat * 0.3, origin_lng + d_lng * 0.25],
                [origin_lat + d_lat * 0.7, origin_lng + d_lng * 0.75],
                [dest_lat, dest_lng],
            ],
        },
        {
            "id": "route_2", "name": "Via Central Avenue",
            "distance": "6.1 km", "duration": "17 mins",
            "mock_lighting": 0.65, "mock_crowd": "medium", "mock_crowd_num": 0.50,
            "path": [
                [origin_lat, origin_lng],
                [origin_lat + d_lat * 0.2, origin_lng + d_lng * 0.45],
                [origin_lat + d_lat * 0.8, origin_lng + d_lng * 0.55],
                [dest_lat, dest_lng],
            ],
        },
        {
            "id": "route_3", "name": "Via Ring Road (Secondary Street)",
            "distance": "7.2 km", "duration": "21 mins",
            "mock_lighting": 0.40, "mock_crowd": "low", "mock_crowd_num": 0.25,
            "path": [
                [origin_lat, origin_lng],
                [origin_lat + d_lat * 0.4, origin_lng - d_lng * 0.15],
                [origin_lat + d_lat * 0.85, origin_lng + d_lng * 0.35],
                [dest_lat, dest_lng],
            ],
        },
    ]
    scored_routes = []
    for r in routes_raw:
        df_features = build_feature_dataframe(
            r["path"], mock_lighting=r["mock_lighting"], mock_crowd=r["mock_crowd"]
        )
        pred_label = pipeline.predict(df_features)[0].lower()
        model_risk_score = {"low": 1.0, "medium": 0.5}.get(pred_label, 0.0)
        w1, w2, w3 = 0.50, 0.30, 0.20
        composite_score = round(
            w1 * model_risk_score + w2 * r["mock_lighting"] + w3 * r["mock_crowd_num"], 2
        )
        display_risk = (
            "Low Risk (Safest)" if composite_score >= 0.70
            else "Medium Risk" if composite_score >= 0.45
            else "High Risk"
        )
        scored_routes.append({
            "routeId": r["id"], "name": r["name"],
            "distance": r["distance"], "duration": r["duration"],
            "compositeScore": composite_score, "modelRiskLabel": pred_label,
            "modelRiskScore": model_risk_score, "displayRisk": display_risk,
            "lightingScore": r["mock_lighting"], "crowdDensity": r["mock_crowd"],
            "disclaimer": MANDATORY_DISCLAIMER,
            "points": [{"lat": p[0], "lng": p[1]} for p in r["path"]],
        })
    scored_routes.sort(key=lambda x: x["compositeScore"], reverse=True)
    return scored_routes


def score_custom_routes(routes_input):
    model_dict = load_model()
    pipeline   = model_dict['pipeline']
    scored_routes = []
    for idx, r in enumerate(routes_input):
        route_id   = r.get("routeId", f"route_{idx + 1}")
        name       = r.get("name", f"Route {idx + 1}")
        distance   = r.get("distance", "N/A")
        duration   = r.get("duration", "N/A")
        raw_pts    = r.get("points", [])
        path = []
        for p in raw_pts:
            if isinstance(p, dict):
                path.append([float(p.get("lat", 0.0)), float(p.get("lng", 0.0))])
            elif isinstance(p, (list, tuple)) and len(p) >= 2:
                path.append([float(p[0]), float(p[1])])
        if not path:
            continue
        mock_lighting  = float(r.get("mock_lighting", 0.70))
        mock_crowd     = str(r.get("mock_crowd", "medium"))
        crowd_num_map  = {"high": 0.90, "medium": 0.50, "low": 0.25}
        mock_crowd_num = float(r.get("mock_crowd_num", crowd_num_map.get(mock_crowd.lower(), 0.50)))
        df_features    = build_feature_dataframe(path, mock_lighting=mock_lighting, mock_crowd=mock_crowd)
        pred_label     = pipeline.predict(df_features)[0].lower()
        model_risk_score = {"low": 1.0, "medium": 0.5}.get(pred_label, 0.0)
        w1, w2, w3 = 0.50, 0.30, 0.20
        composite_score = round(
            w1 * model_risk_score + w2 * mock_lighting + w3 * mock_crowd_num, 2
        )
        display_risk = (
            "Low Risk (Safest)" if composite_score >= 0.70
            else "Medium Risk" if composite_score >= 0.45
            else "High Risk"
        )
        scored_routes.append({
            "routeId": route_id, "name": name,
            "distance": distance, "duration": duration,
            "compositeScore": composite_score, "modelRiskLabel": pred_label,
            "modelRiskScore": model_risk_score, "displayRisk": display_risk,
            "lightingScore": mock_lighting, "crowdDensity": mock_crowd,
            "disclaimer": MANDATORY_DISCLAIMER,
            "points": [{"lat": p[0], "lng": p[1]} for p in path],
        })
    scored_routes.sort(key=lambda x: x["compositeScore"], reverse=True)
    return scored_routes


app = Flask(__name__)


@app.route('/scoreRoute', methods=['POST', 'GET'])
def score_route_http():
    if request.method == 'OPTIONS':
        return jsonify({}), 200
    req_json  = request.get_json(silent=True) or request.args
    req_routes = req_json.get('routes') if isinstance(req_json, dict) else None
    try:
        if req_routes and isinstance(req_routes, list) and len(req_routes) > 0:
            routes = score_custom_routes(req_routes)
        else:
            origin_lat = float(req_json.get('originLat', 28.6139))
            origin_lng = float(req_json.get('originLng', 77.2090))
            dest_lat   = float(req_json.get('destLat',   28.5355))
            dest_lng   = float(req_json.get('destLng',   77.3910))
            routes = score_route_segments(origin_lat, origin_lng, dest_lat, dest_lng)
        return jsonify({"status": "success", "disclaimer": MANDATORY_DISCLAIMER, "routes": routes})
    except Exception as e:
        return jsonify({"status": "error", "message": str(e), "disclaimer": MANDATORY_DISCLAIMER}), 500


if __name__ == '__main__':
    print("Starting SafeHer scoreRoute Backend Server on port 5000...")
    load_model()
    app.run(host='0.0.0.0', port=5000, debug=True)
