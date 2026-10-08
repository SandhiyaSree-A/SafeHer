"""
seed_sos_hotspots.py
────────────────────────────────────────────────────────────────
Creates 4 realistic SOS hotspot clusters in Chennai for demo/testing.
Writes directly to Firestore using the Admin SDK (bypasses client rules).

Usage:
  export GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json
  python seed_sos_hotspots.py

The script is idempotent: re-running it overwrites the same cell IDs.
Ethical disclaimer: Risk-awareness estimate for prototype/demo purposes only,
not a guarantee of real-world safety or crime prediction.
"""
import time
import firebase_admin
from firebase_admin import credentials, firestore

# ---------------------------------------------------------------------------
# Initialise Firebase Admin SDK
# ---------------------------------------------------------------------------
import os
sa_path = os.environ.get(
    "GOOGLE_APPLICATION_CREDENTIALS",
    os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json"),
)
if not firebase_admin._apps:
    if os.path.exists(sa_path):
        cred = credentials.Certificate(sa_path)
        firebase_admin.initialize_app(cred)
    else:
        firebase_admin.initialize_app()   # uses ADC on GCP

db = firestore.client()

# ---------------------------------------------------------------------------
# Demo hotspot clusters (Chennai)
# ---------------------------------------------------------------------------
CELL_SIZE_DEG = 0.0014   # must match functions/main.py


def cell_id(lat: float, lng: float) -> str:
    clat = round(lat / CELL_SIZE_DEG) * CELL_SIZE_DEG
    clng = round(lng / CELL_SIZE_DEG) * CELL_SIZE_DEG
    return f"{clat:.4f}_{clng:.4f}"


now_ms          = int(time.time() * 1000)
ninety_days_ms  = 90 * 24 * 3600 * 1000

HOTSPOTS = [
    {
        # Koyambedu Bus Terminus area – busy late-night pick-up zone
        "lat": 13.0694, "lng": 80.1948,
        "alertCount": 8, "uniqueUserCount": 5,
        "recentCount_90d": 7, "nightCount": 6,
        "lastAlertAt": now_ms - 5 * 24 * 3600 * 1000,  # 5 days ago
    },
    {
        # Egmore Railway Station surroundings
        "lat": 13.0782, "lng": 80.2605,
        "alertCount": 5, "uniqueUserCount": 3,
        "recentCount_90d": 5, "nightCount": 2,
        "lastAlertAt": now_ms - 15 * 24 * 3600 * 1000,  # 15 days ago
    },
    {
        # T. Nagar shopping area backstreets – older cluster
        "lat": 13.0418, "lng": 80.2341,
        "alertCount": 4, "uniqueUserCount": 3,
        "recentCount_90d": 2, "nightCount": 3,
        "lastAlertAt": now_ms - 110 * 24 * 3600 * 1000,  # ~3.5 months ago (time-decayed)
    },
    {
        # Anna Nagar West side road
        "lat": 13.0878, "lng": 80.2089,
        "alertCount": 6, "uniqueUserCount": 4,
        "recentCount_90d": 6, "nightCount": 5,
        "lastAlertAt": now_ms - 2 * 24 * 3600 * 1000,   # 2 days ago
    },
]

batch = db.batch()
for hs in HOTSPOTS:
    cid = cell_id(hs["lat"], hs["lng"])
    ref = db.collection("sos_hotspots").document(cid)
    batch.set(ref, {
        "centerLat":       round(round(hs["lat"] / CELL_SIZE_DEG) * CELL_SIZE_DEG, 4),
        "centerLng":       round(round(hs["lng"] / CELL_SIZE_DEG) * CELL_SIZE_DEG, 4),
        "alertCount":      hs["alertCount"],
        "uniqueUserCount": hs["uniqueUserCount"],
        "recentCount_90d": hs["recentCount_90d"],
        "nightCount":      hs["nightCount"],
        "lastAlertAt":     hs["lastAlertAt"],
        "isFalseAlarm":    False,
        # _userIds omitted from seed – only the Cloud Function writes it
    })
    print(f"  Seeded hotspot: {cid}  alerts={hs['alertCount']}  users={hs['uniqueUserCount']}")

batch.commit()
print("\nSeed complete. 4 SOS hotspot clusters written to Firestore.")
print("Disclaimer: Risk-awareness estimate for prototype/demo purposes only,")
print("not a guarantee of real-world safety or crime prediction.")
