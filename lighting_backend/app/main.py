import os
import hashlib
import hmac
import logging
import uuid
import requests
from datetime import datetime, timezone
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

logger = logging.getLogger(__name__)

SOS_HASH_SALT = os.environ.get("SOS_HASH_SALT", "default_secret_salt").encode('utf-8')
DATABASE_URL = os.environ.get("DATABASE_URL")
_pg_pool = None

def get_pg_pool():
    global _pg_pool
    if _pg_pool is None and DATABASE_URL:
        try:
            from psycopg_pool import ConnectionPool
            _pg_pool = ConnectionPool(DATABASE_URL, min_size=1, max_size=5)
        except Exception as e:
            logger.warning("Failed to init psycopg pool in main.py: %s", e)
    return _pg_pool

from app.services.routing_service import get_routes
from app.services.route_safety_score_service import analyze_multi_factor_routes
from fastapi.middleware.cors import CORSMiddleware


# ----------------------------------
# CREATE FASTAPI APP
# ----------------------------------

app = FastAPI(
    title="SafeHer Lighting API",
    description=(
        "Route safety analysis using "
        "NASA Black Marble nighttime lights"
    ),
    version="1.0"
)

# ----------------------------------
# CORS CONFIGURATION
# ----------------------------------

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173"
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)

# ----------------------------------
# REQUEST MODEL
# ----------------------------------

class RouteRequest(BaseModel):

    source_lat: float
    source_lon: float

    destination_lat: float
    destination_lon: float

    mode: str = "driving"  # driving | walking | bicycling

    # Optional fields for trust score persistence
    user_id: str = ""
    session_id: str = ""
    destination_address: str = ""

class SosEventRequest(BaseModel):
    alertId: str
    userId: str
    lat: float
    lng: float
    timestamp: int  # milliseconds epoch

# ----------------------------------
# HOME ENDPOINT
# ----------------------------------

@app.get("/")
def home():

    return {
        "message": "SafeHer Lighting Backend is Running",
        "status": "success"
    }

@app.get("/geocode")
def geocode_destination(query: str):

    try:

        response = requests.get(
            "https://nominatim.openstreetmap.org/search",
            params={
                "q": query,
                "format": "jsonv2",
                "limit": 1
            },
            headers={
                "User-Agent": "SafeHer-Hackathon-App/1.0"
            },
            timeout=20
        )

        response.raise_for_status()

        results = response.json()

        if not results:
            raise HTTPException(
                status_code=404,
                detail="Destination not found"
            )

        location = results[0]

        return {
            "status": "success",
            "latitude": float(location["lat"]),
            "longitude": float(location["lon"]),
            "display_name": location["display_name"]
        }

    except HTTPException:
        raise

    except Exception as error:

        print("GEOCODING ERROR:", str(error))

        raise HTTPException(
            status_code=500,
            detail=str(error)
        )

# ----------------------------------
# ROUTE ANALYSIS ENDPOINT
# ----------------------------------

@app.post("/analyze-route")
def analyze_route(request: RouteRequest):

    try:

        # ----------------------------------
        # VALIDATE COORDINATES
        # ----------------------------------

        if not -90 <= request.source_lat <= 90:
            raise HTTPException(
                status_code=400,
                detail="Invalid source latitude"
            )

        if not -180 <= request.source_lon <= 180:
            raise HTTPException(
                status_code=400,
                detail="Invalid source longitude"
            )

        if not -90 <= request.destination_lat <= 90:
            raise HTTPException(
                status_code=400,
                detail="Invalid destination latitude"
            )

        if not -180 <= request.destination_lon <= 180:
            raise HTTPException(
                status_code=400,
                detail="Invalid destination longitude"
            )

        # ----------------------------------
        # GET ROUTES FROM OSRM
        # ----------------------------------

        routes = get_routes(
            request.source_lat,
            request.source_lon,
            request.destination_lat,
            request.destination_lon,
            mode=request.mode
        )

        if not routes:
            raise HTTPException(
                status_code=404,
                detail="No routes found"
            )

        # ----------------------------------
        # ANALYZE ALL ROUTES
        # ----------------------------------

        result = analyze_multi_factor_routes(
            routes,
            interval_meters=300
        )

        # ----------------------------------
        # GET SAFEST ROUTE
        # ----------------------------------

        safest = result["safest_route"]

        lighting = safest["lighting"]

        darkest = lighting.get("darkest_point")

        dark_analysis = lighting.get(
            "dark_stretch_analysis",
            {}
        )

        safety = lighting.get(
            "lighting_safety",
            {}
        )

        # ----------------------------------
        # SAFEST ROUTE RESPONSE
        # ----------------------------------

        safest_route_response = {

            "route_id": safest["route_id"],

            "distance_km": round(
                safest["distance_meters"] / 1000,
                2
            ),

            "duration_minutes": round(
                safest["duration_seconds"] / 60,
                2
            ),

            # Route coordinates for map
            "coordinates": safest.get(
                "coordinates",
                []
            ),

            # Safety information
            "safety": {

                "lighting_safety_score": safety.get(
                    "lighting_safety_score",
                    0
                ),

                "average_light_score": lighting.get(
                    "average_light_score",
                    0
                ),

                "darkest_point_score": (
                    darkest.get("light_score")
                    if darkest
                    else None
                ),

                "dark_points": dark_analysis.get(
                    "total_dark_points",
                    0
                ),

                "dark_stretches": dark_analysis.get(
                    "total_dark_stretches",
                    0
                )
            },

            # Darkest location
            "darkest_location": (

                {
                    "latitude": darkest.get("latitude"),

                    "longitude": darkest.get("longitude"),

                    "light_score": darkest.get(
                        "light_score"
                    )
                }

                if darkest

                else None
            ),

            # All sampled NASA lighting points
            "lighting_points": lighting.get(
                "points",
                []
            )
        }

        # ----------------------------------
        # BUILD ALL ROUTES RESPONSE
        # ----------------------------------

        all_routes = []

        for route in result["routes"]:

            route_lighting = route.get(
                "lighting",
                {}
            )

            route_safety = route_lighting.get(
                "lighting_safety",
                {}
            )

            route_darkest = route_lighting.get(
                "darkest_point"
            )

            route_id = route["route_id"]
            
            factors = route.get("factors", {})

            traffic_score        = factors.get("traffic_score") or 0
            human_score          = factors.get("human_presence_score") or 0
            activity_score       = factors.get("activity_density_score") or 0
            pedestrian_score     = factors.get("pedestrian_score") or 0
            confidence           = factors.get("confidence") or 1.0
            composite            = factors.get("final_safety_score") or 0
            sos_hotspot_score_val = factors.get("sos_hotspot_score")   # None when unavailable

            # Proxied textual condition
            traffic_condition = "Smooth Traffic" if traffic_score > 60 else ("Moderate Traffic" if traffic_score > 30 else "Congested Traffic")
            crowd_density = "HIGH" if human_score > 60 else ("MEDIUM" if human_score > 30 else "LOW")

            all_routes.append({
                "route_id": route["route_id"],
                "distance_km": round(route["distance_meters"] / 1000, 2),
                "duration_minutes": round(route["duration_seconds"] / 60, 2),
                "lighting_safety_score": route_safety.get("lighting_safety_score", 0),
                "average_light_score": route_lighting.get("average_light_score", 0),
                "crowd_density": crowd_density,
                "traffic_condition": traffic_condition,

                # Factor scores
                "traffic_score":          traffic_score,
                "human_presence_score":   human_score,
                "activity_density_score": activity_score,
                "pedestrian_score":       pedestrian_score,
                "confidence_score":       confidence,
                "composite_score":        composite,

                # Dark stretch stats (for trust score persistence)
                "dark_points":    route_lighting.get("dark_stretch_analysis", {}).get("total_dark_points", 0),
                "dark_stretches": route_lighting.get("dark_stretch_analysis", {}).get("total_dark_stretches", 0),

                # SOS Hotspot factor (None when Firestore unavailable)
                "sos_hotspot_score":  sos_hotspot_score_val,
                "sos_hotspot_count":  route.get("sos_hotspot_count", 0),
                "sos_hotspot_points": route.get("sos_hotspot_points", []),

                "via_route": route.get("via_route", ""),
                "darkest_point_score": route_darkest.get("light_score") if route_darkest else None,
                "coordinates": route.get("coordinates", []),

                # Ethical disclaimer
                "disclaimer": (
                    "Risk-awareness estimate for prototype/demo purposes only, "
                    "not a guarantee of real-world safety or crime prediction"
                ),
            })

        # ----------------------------------
        # PERSIST TRUST SCORES TO DATABASE
        # ----------------------------------

        pool = get_pg_pool()
        historical_averages = {}  # route_id -> dict of avg scores

        if pool:
            try:
                with pool.connection() as conn:
                    with conn.cursor() as cur:

                        # ── 1. Fetch historical averages for this corridor ────────
                        # Matches destination within ~500 m (0.005 degrees ≈ 500 m)
                        cur.execute("""
                            SELECT
                                route_id,
                                COUNT(*) AS history_count,
                                AVG(lighting_safety_score)  AS avg_lighting_safety,
                                AVG(average_light_score)    AS avg_light_score,
                                AVG(traffic_score)          AS avg_traffic,
                                AVG(human_presence_score)   AS avg_human_presence,
                                AVG(activity_density_score) AS avg_activity_density,
                                AVG(pedestrian_score)       AS avg_pedestrian,
                                AVG(sos_hotspot_score)      AS avg_sos_hotspot,
                                AVG(composite_score)        AS avg_composite,
                                AVG(confidence)             AS avg_confidence
                            FROM route_trust_scores
                            WHERE
                                ABS(dest_lat - %s) < 0.005
                                AND ABS(dest_lng - %s) < 0.005
                                AND transport_mode = %s
                            GROUP BY route_id
                        """, (
                            request.destination_lat,
                            request.destination_lon,
                            request.mode
                        ))

                        for row in cur.fetchall():
                            historical_averages[row[0]] = {
                                "history_count":        row[1],
                                "avg_lighting_safety":  row[2],
                                "avg_light_score":      row[3],
                                "avg_traffic":          row[4],
                                "avg_human_presence":   row[5],
                                "avg_activity_density": row[6],
                                "avg_pedestrian":       row[7],
                                "avg_sos_hotspot":      row[8],
                                "avg_composite":        row[9],
                                "avg_confidence":       row[10],
                            }

                        # ── 2. Insert fresh scores for every route in this call ──
                        now = datetime.now(tz=timezone.utc)

                        for r in all_routes:
                            cur.execute("""
                                INSERT INTO route_trust_scores (
                                    id, route_id, user_id, session_id,
                                    origin_lat, origin_lng,
                                    dest_lat, dest_lng,
                                    destination_address, transport_mode,
                                    lighting_safety_score, average_light_score,
                                    traffic_score, human_presence_score,
                                    activity_density_score, pedestrian_score,
                                    sos_hotspot_score, sos_hotspot_count,
                                    confidence, composite_score,
                                    distance_km, duration_minutes,
                                    dark_points, dark_stretches,
                                    computed_at
                                ) VALUES (
                                    %s, %s, %s, %s,
                                    %s, %s, %s, %s, %s, %s,
                                    %s, %s, %s, %s, %s, %s,
                                    %s, %s, %s, %s,
                                    %s, %s, %s, %s, %s
                                )
                                ON CONFLICT (id) DO NOTHING
                            """, (
                                str(uuid.uuid4()),
                                r["route_id"],
                                request.user_id or None,
                                request.session_id or None,
                                request.source_lat,
                                request.source_lon,
                                request.destination_lat,
                                request.destination_lon,
                                request.destination_address or None,
                                request.mode,
                                r.get("lighting_safety_score", 0.0),
                                r.get("average_light_score", 0.0),
                                r.get("traffic_score", 0.0),
                                r.get("human_presence_score", 0.0),
                                r.get("activity_density_score", 0.0),
                                r.get("pedestrian_score", 0.0),
                                r.get("sos_hotspot_score"),
                                r.get("sos_hotspot_count", 0),
                                r.get("confidence_score", 1.0),
                                r.get("composite_score", 0.0),
                                r.get("distance_km"),
                                r.get("duration_minutes"),
                                r.get("dark_points", 0),
                                r.get("dark_stretches", 0),
                                now
                            ))

                    conn.commit()
            except Exception as db_err:
                logger.warning("Trust score DB operation failed: %s", db_err)

        # ── Attach historical context to each route in the response ───────────
        for r in all_routes:
            hist = historical_averages.get(r["route_id"])
            if hist:
                r["historical_trust"] = {
                    "history_count":        hist["history_count"],
                    "avg_composite_score":  round(hist["avg_composite"] or 0, 2),
                    "avg_lighting_safety":  round(hist["avg_lighting_safety"] or 0, 2),
                    "avg_traffic":          round(hist["avg_traffic"] or 0, 2),
                    "avg_human_presence":   round(hist["avg_human_presence"] or 0, 2),
                    "avg_pedestrian":       round(hist["avg_pedestrian"] or 0, 2),
                    "avg_sos_hotspot":      round(hist["avg_sos_hotspot"] or 0, 2) if hist["avg_sos_hotspot"] is not None else None,
                    "avg_confidence":       round(hist["avg_confidence"] or 1.0, 3),
                }
            else:
                r["historical_trust"] = None  # no prior data for this route

        # ----------------------------------
        # FINAL API RESPONSE
        # ----------------------------------

        return {

            "status": "success",

            "total_routes": result["total_routes"],

            "safest_route": safest_route_response,

            "all_routes": all_routes
        }


    # ----------------------------------
    # HANDLE HTTP ERRORS
    # ----------------------------------

    except HTTPException:
        raise


    # ----------------------------------
    # HANDLE UNEXPECTED ERRORS
    # ----------------------------------

    except Exception as error:

        print("\nERROR:", str(error))

        raise HTTPException(
            status_code=500,
            detail=str(error)
        )

# ----------------------------------
# SOS EVENTS ENDPOINT
# ----------------------------------
@app.post("/sos-events")
def create_sos_event(event: SosEventRequest):
    pool = get_pg_pool()
    if not pool:
        # DB empty or unreachable = fallback logic / do not crash
        logger.warning("Database not configured or unreachable for /sos-events")
        return {"status": "ignored", "reason": "database_unavailable"}

    user_hash = hmac.new(SOS_HASH_SALT, event.userId.encode('utf-8'), hashlib.sha256).hexdigest()

    try:
        with pool.connection() as conn:
            with conn.cursor() as cur:
                from datetime import datetime, timezone
                dt = datetime.fromtimestamp(event.timestamp / 1000.0, tz=timezone.utc)
                
                cur.execute("""
                    SELECT id FROM sos_events 
                    WHERE user_hash = %s 
                      AND ST_DWithin(geom, ST_SetSRID(ST_MakePoint(%s, %s), 4326), 150)
                      AND triggered_at >= %s - INTERVAL '24 hours'
                    LIMIT 1
                """, (user_hash, event.lng, event.lat, dt))
                
                if cur.fetchone():
                    return {"status": "ignored", "reason": "duplicate_within_24h_150m"}

                cur.execute("""
                    INSERT INTO sos_events (id, user_hash, geom, triggered_at)
                    VALUES (%s, %s, ST_SetSRID(ST_MakePoint(%s, %s), 4326), %s)
                    ON CONFLICT (id) DO NOTHING
                """, (event.alertId, user_hash, event.lng, event.lat, dt))
                
            conn.commit()
        return {"status": "success", "alertId": event.alertId}
    except Exception as e:
        logger.error(f"Error inserting SOS event: {e}", exc_info=True)
        # We don't want to crash the caller for background SOS tracking
        return {"status": "error", "reason": str(e)}

# ----------------------------------
# ROUTE FEEDBACK ENDPOINT
# ----------------------------------

class RouteFeedbackRequest(BaseModel):
    id: str = ""
    journey_id: str = ""
    user_id: str = ""
    route_id: str = ""
    route_name: str = ""
    origin_lat: float = 0.0
    origin_lng: float = 0.0
    dest_lat: float = 0.0
    dest_lng: float = 0.0
    destination_address: str = ""
    transport_mode: str = "driving"
    was_safe: bool = False
    reasons: str = ""         # semicolon-separated
    custom_reason: str = ""

@app.post("/route-feedback")
def save_route_feedback(req: RouteFeedbackRequest):
    pool = get_pg_pool()
    if not pool:
        logger.warning("Database not configured for /route-feedback")
        return {"status": "ignored", "reason": "database_unavailable"}

    record_id = req.id if req.id else str(uuid.uuid4())

    try:
        with pool.connection() as conn:
            with conn.cursor() as cur:
                cur.execute("""
                    INSERT INTO route_feedback (
                        id, journey_id, user_id, route_id, route_name,
                        origin_lat, origin_lng, dest_lat, dest_lng,
                        destination_address, transport_mode,
                        was_safe, reasons, custom_reason, submitted_at
                    ) VALUES (
                        %s, %s, %s, %s, %s,
                        %s, %s, %s, %s,
                        %s, %s,
                        %s, %s, %s, NOW()
                    )
                    ON CONFLICT (id) DO NOTHING
                """, (
                    record_id, req.journey_id, req.user_id,
                    req.route_id, req.route_name,
                    req.origin_lat, req.origin_lng,
                    req.dest_lat, req.dest_lng,
                    req.destination_address, req.transport_mode,
                    req.was_safe, req.reasons, req.custom_reason
                ))
            conn.commit()
        return {"status": "success", "id": record_id}
    except Exception as e:
        logger.error(f"Error saving route feedback: {e}", exc_info=True)
        return {"status": "error", "reason": str(e)}

# ----------------------------------
# SAFEST ROUTES ENDPOINT
# ----------------------------------

class SafestRouteRequest(BaseModel):
    id: str = ""
    journey_id: str = ""
    user_id: str = ""
    route_id: str = ""
    route_name: str = ""
    origin_lat: float = 0.0
    origin_lng: float = 0.0
    dest_lat: float = 0.0
    dest_lng: float = 0.0
    destination_address: str = ""
    transport_mode: str = "driving"
    composite_score: float = 0.0
    risk_label: str = ""
    distance: str = ""
    duration: str = ""

@app.post("/safest-routes")
def save_safest_route(req: SafestRouteRequest):
    pool = get_pg_pool()
    if not pool:
        logger.warning("Database not configured for /safest-routes")
        return {"status": "ignored", "reason": "database_unavailable"}

    record_id = req.id if req.id else str(uuid.uuid4())

    try:
        with pool.connection() as conn:
            with conn.cursor() as cur:
                cur.execute("""
                    INSERT INTO safest_routes (
                        id, journey_id, user_id, route_id, route_name,
                        origin_lat, origin_lng, dest_lat, dest_lng,
                        destination_address, transport_mode,
                        composite_score, risk_label, distance, duration,
                        marked_safe_at
                    ) VALUES (
                        %s, %s, %s, %s, %s,
                        %s, %s, %s, %s,
                        %s, %s,
                        %s, %s, %s, %s, NOW()
                    )
                    ON CONFLICT (id) DO NOTHING
                """, (
                    record_id, req.journey_id, req.user_id,
                    req.route_id, req.route_name,
                    req.origin_lat, req.origin_lng,
                    req.dest_lat, req.dest_lng,
                    req.destination_address, req.transport_mode,
                    req.composite_score, req.risk_label,
                    req.distance, req.duration
                ))
            conn.commit()
        return {"status": "success", "id": record_id}
    except Exception as e:
        logger.error(f"Error saving safest route: {e}", exc_info=True)
        return {"status": "error", "reason": str(e)}