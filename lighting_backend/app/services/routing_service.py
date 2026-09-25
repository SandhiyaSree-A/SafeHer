import os
import requests

# Load Google Maps API Key from local.properties or env
def get_maps_api_key():
    key = os.environ.get("SERVER_MAPS_API_KEY") or os.environ.get("MAPS_API_KEY")
    if key: return key
    try:
        # Check parent directories for local.properties
        current_dir = os.path.dirname(os.path.abspath(__file__))
        project_root = os.path.abspath(os.path.join(current_dir, "../../../"))
        with open(os.path.join(project_root, "local.properties"), "r") as f:
            lines = f.readlines()
            # Check for SERVER_MAPS_API_KEY first
            for line in lines:
                if line.startswith("SERVER_MAPS_API_KEY="):
                    return line.split("=")[1].strip()
            # Fallback to MAPS_API_KEY
            for line in lines:
                if line.startswith("MAPS_API_KEY="):
                    return line.split("=")[1].strip()
    except Exception:
        pass
    return None

def get_routes(
    source_lat: float,
    source_lon: float,
    destination_lat: float,
    destination_lon: float,
    mode: str = "driving"
):
    """
    Get alternative routes (driving, walking, bike) from Google Maps Directions API
    with traffic duration and polyline.
    """
    osrm_mode = "driving"
    if mode in ["walking", "walk"]:
        osrm_mode = "foot"
    elif mode in ["bicycling", "bike"]:
        osrm_mode = "bike"

    url = f"http://router.project-osrm.org/route/v1/{osrm_mode}/{source_lon},{source_lat};{destination_lon},{destination_lat}"
    params = {
        "alternatives": "3",
        "geometries": "polyline",
        "overview": "full"
    }

    print(f"\nRequesting real-time {mode} routes from OSRM API...")

    response = requests.get(url, params=params, timeout=30)
    if response.status_code != 200:
        raise RuntimeError(f"OSRM API failed with status {response.status_code}")

    data = response.json()
    if data.get("code") != "Ok":
        if data.get("code") == "NoRoute":
            return []
        raise RuntimeError(f"OSRM API returned error: {data.get('code')} - {data.get('message', '')}")

    routes = []
    for index, route in enumerate(data.get("routes", [])):
        # Decode overview_polyline
        import polyline
        encoded_polyline = route["geometry"]
        coordinates = polyline.decode(encoded_polyline)
        route_points = [
            {"latitude": coord[0], "longitude": coord[1]}
            for coord in coordinates
        ]

        distance_meters = route.get("distance", 0.0)
        duration_seconds = route.get("duration", 0.0)
        duration_in_traffic_seconds = duration_seconds # OSRM doesn't have traffic

        leg = route.get("legs", [{}])[0]
        summary_name = leg.get("summary", "")
        via_route_str = f"via {summary_name}" if summary_name else f"via Alternative Route {index + 1}"

        routes.append({
            "route_id": index + 1,
            "transport_mode": mode,
            "distance_meters": distance_meters,
            "duration_seconds": duration_seconds,
            "duration_in_traffic_seconds": duration_in_traffic_seconds,
            "via_route": via_route_str,
            "street_names": [summary_name] if summary_name else [],
            "coordinates": route_points,
            "encoded_polyline": encoded_polyline
        })

    return routes