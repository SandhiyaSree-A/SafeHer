"""
test_sos_hotspot_service.py
Unit tests for services/sos_hotspot_service.py (Postgres/Firestore compatible)

Run:
  cd lighting_backend
  python -m pytest app/test_sos_hotspot_service.py -v

Tests
─────
(a) no hotspots in cache  -> score 100
(b) route through hotspot -> lower score than route 1 km away
(c) single user spam      -> does NOT qualify as a hotspot
(d) night hour            -> lower score than day hour for night-heavy hotspot
"""

import sys, os, time, types, math

# ---------------------------------------------------------------------------
# Patch firebase_admin so tests run without a service account
# ---------------------------------------------------------------------------
firebase_admin_stub = types.ModuleType("firebase_admin")
firebase_admin_stub._apps = {}
firebase_admin_stub.initialize_app = lambda *a, **k: None
firebase_credentials_stub = types.ModuleType("firebase_admin.credentials")
firebase_firestore_stub   = types.ModuleType("firebase_admin.firestore")
firebase_admin_stub.credentials = firebase_credentials_stub
firebase_admin_stub.firestore   = firebase_firestore_stub
sys.modules["firebase_admin"]             = firebase_admin_stub
sys.modules["firebase_admin.credentials"] = firebase_credentials_stub
sys.modules["firebase_admin.firestore"]   = firebase_firestore_stub

# Patch psycopg_pool
psycopg_pool_stub = types.ModuleType("psycopg_pool")
class DummyPool:
    def connection(self):
        class DummyConn:
            def __enter__(self):
                return self
            def __exit__(self, exc_type, exc_val, exc_tb):
                pass
            def cursor(self):
                class DummyCursor:
                    def __enter__(self):
                        return self
                    def __exit__(self, exc_type, exc_val, exc_tb):
                        pass
                    def execute(self, *a, **k):
                        pass
                    def __iter__(self):
                        return iter([])
                return DummyCursor()
        return DummyConn()

psycopg_pool_stub.ConnectionPool = lambda *a, **k: DummyPool()
sys.modules["psycopg_pool"] = psycopg_pool_stub

# Now import the service under test
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import importlib
import app.services.sos_hotspot_service as svc

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _patch_cache(hotspots):
    """Directly inject hotspots into the module cache."""
    with svc._cache_lock:
        svc._hotspots_cache  = hotspots
        svc._cache_loaded_at = time.time()
        svc._init_failed = False


def _make_hotspot(lat, lng, alert_count=5, unique_users=3,
                  night_count=0, weighted_count=5.0):
    return {
        "id":             f"{lat:.4f}_{lng:.4f}",
        "lat":            lat,
        "lng":            lng,
        "alert_count":    alert_count,
        "weighted_count": weighted_count,
        "unique_users":   unique_users,
        "night_count":    night_count,
    }


# ---------------------------------------------------------------------------
# (a) No hotspots -> score 100
# ---------------------------------------------------------------------------
def test_no_hotspots_returns_100():
    _patch_cache([])
    score, reason = svc.calculate_sos_hotspot_score(13.0694, 80.1948, hour=12)
    assert score == 100.0, f"Expected 100 but got {score}"
    assert reason is None
    print("PASS  (a) no hotspots -> 100")


# ---------------------------------------------------------------------------
# (b) Route through hotspot scores lower than route 1 km away
# ---------------------------------------------------------------------------
def test_nearby_hotspot_lowers_score():
    hotspot_lat, hotspot_lng = 13.0694, 80.1948
    hs = _make_hotspot(hotspot_lat, hotspot_lng, alert_count=8, unique_users=5,
                       weighted_count=8.0)
    _patch_cache([hs])

    # Point A: right on top of the hotspot
    score_on, _ = svc.calculate_sos_hotspot_score(hotspot_lat, hotspot_lng, hour=12)

    # Point B: ~1 km north (well outside 200 m radius)
    far_lat = hotspot_lat + 0.009   # approx 1 km
    score_far, _ = svc.calculate_sos_hotspot_score(far_lat, hotspot_lng, hour=12)

    assert score_far == 100.0, f"Far point should be 100, got {score_far}"
    assert score_on < score_far, (
        f"Route through hotspot ({score_on}) should score lower than "
        f"route 1 km away ({score_far})"
    )
    print(f"PASS  (b) hotspot: on={score_on:.1f} < far={score_far:.1f}")


# ---------------------------------------------------------------------------
# (c) Single user spamming SOS does NOT create a hotspot
# ---------------------------------------------------------------------------
def test_single_user_spam_not_hotspot():
    """
    We simulate the result by injecting a row that would not qualify (1 unique user).
    """
    # Mock Postgres cursor
    class FakeCursor:
        def __enter__(self): return self
        def __exit__(self, *a): pass
        def execute(self, *a, **k): pass
        def __iter__(self):
            now_ms = int(time.time() * 1000)
            yield ("cid1", 13.0694, 80.1948, 10, 1, 0, now_ms - 1000)

    class FakeConn:
        def __enter__(self): return self
        def __exit__(self, *a): pass
        def cursor(self): return FakeCursor()

    class FakePool:
        def connection(self): return FakeConn()

    # Temporarily replace _get_pg_pool
    original_pool = svc._get_pg_pool
    svc._get_pg_pool = lambda: FakePool()
    
    with svc._cache_lock:
        svc._cache_loaded_at = 0.0
        svc._init_failed = False

    try:
        success = svc._load_hotspots_postgres()
        assert success, "Load should succeed with fake pg"
        with svc._cache_lock:
            cache = list(svc._hotspots_cache)
        assert len(cache) == 0, (
            f"Single-user cell should NOT appear in hotspot cache, got {cache}"
        )
    finally:
        svc._get_pg_pool = original_pool

    print("PASS  (c) single-user spam not admitted to hotspot cache")


# ---------------------------------------------------------------------------
# (d) Night time -> lower score than day time for a night-heavy hotspot
# ---------------------------------------------------------------------------
def test_night_lowers_score_more_than_day():
    hotspot_lat, hotspot_lng = 13.0694, 80.1948
    # night_count is 80% of alert_count -> night_ratio = 0.8 >= 0.4 threshold
    # weighted_count=3.0 keeps day score > 0 so the night multiplier is visible
    hs = _make_hotspot(hotspot_lat, hotspot_lng,
                       alert_count=10, unique_users=4,
                       night_count=8, weighted_count=3.0)
    _patch_cache([hs])

    # Score at midday (hour=12) -- no night multiplier
    score_day,   _ = svc.calculate_sos_hotspot_score(hotspot_lat, hotspot_lng, hour=12)
    # Score at midnight (hour=0) -- night multiplier applied
    score_night, _ = svc.calculate_sos_hotspot_score(hotspot_lat, hotspot_lng, hour=0)

    assert score_night < score_day, (
        f"Night score ({score_night}) should be lower than day score ({score_day})"
    )
    print(f"PASS  (d) night score {score_night:.1f} < day score {score_day:.1f}")


# ---------------------------------------------------------------------------
# Run all tests
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    print("\n=== SOS Hotspot Service Unit Tests ===")
    test_no_hotspots_returns_100()
    test_nearby_hotspot_lowers_score()
    test_single_user_spam_not_hotspot()
    test_night_lowers_score_more_than_day()
    print("All tests passed.\n")
