import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.services.routing_service import get_routes
from app.services.safe_route_service import analyze_routes
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
            request.destination_lon
        )

        if not routes:
            raise HTTPException(
                status_code=404,
                detail="No routes found"
            )

        # ----------------------------------
        # ANALYZE ALL ROUTES
        # ----------------------------------

        result = analyze_routes(
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
            
            # Real-time traffic scoring
            duration = route.get("duration_seconds", 1)
            duration_traffic = route.get("duration_in_traffic_seconds", duration)
            
            # If duration in traffic is much higher than free flow, it's congested.
            traffic_ratio = duration_traffic / max(duration, 1)
            
            if traffic_ratio <= 1.05:
                traffic_condition = "Smooth Traffic"
                traffic_score = 0.90
            elif traffic_ratio <= 1.25:
                traffic_condition = "Moderate Traffic"
                traffic_score = 0.65
            else:
                traffic_condition = "Congested Traffic"
                traffic_score = 0.40

            # Proxied crowd density based on route ranking and traffic
            crowd_density = "HIGH" if route_id == result["safest_route_id"] else ("MEDIUM" if traffic_score > 0.5 else "LOW")

            all_routes.append({

                "route_id": route["route_id"],

                "distance_km": round(
                    route["distance_meters"] / 1000,
                    2
                ),

                "duration_minutes": round(
                    route["duration_seconds"] / 60,
                    2
                ),

                "lighting_safety_score": route_safety.get(
                    "lighting_safety_score",
                    0
                ),

                "average_light_score": route_lighting.get(
                    "average_light_score",
                    0
                ),

                "crowd_density": crowd_density,

                "traffic_condition": traffic_condition,

                "traffic_score": traffic_score,

                "via_route": route.get("via_route", ""),

                "darkest_point_score": (
                    route_darkest.get("light_score")
                    if route_darkest
                    else None
                ),

                # Route coordinates for map
                "coordinates": route.get(
                    "coordinates",
                    []
                )
            })

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