import os
import requests

# Load Google Maps API Key from local.properties or env
def get_maps_api_key():
    key = os.environ.get("MAPS_API_KEY")
    if key: return key
    try:
        # Check parent directories for local.properties
        current_dir = os.path.dirname(os.path.abspath(__file__))
        project_root = os.path.abspath(os.path.join(current_dir, "../../../"))
        with open(os.path.join(project_root, "local.properties"), "r") as f:
            for line in f:
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
    api_key = get_maps_api_key()
    if not api_key:
        raise RuntimeError("MAPS_API_KEY is not set or found in local.properties")
        
    gmaps_mode = "driving"
    if mode in ["walking", "walk"]:
        gmaps_mode = "walking"
    elif mode in ["bicycling", "bike"]:
        gmaps_mode = "bicycling"

    url = "https://maps.googleapis.com/maps/api/directions/json"
    params = {
        "origin": f"{source_lat},{source_lon}",
        "destination": f"{destination_lat},{destination_lon}",
        "mode": gmaps_mode,
        "alternatives": "true",
        "departure_time": "now",
        "key": api_key
    }

    print(f"\nRequesting real-time {mode} routes from Google Maps API...")

    response = requests.get(url, params=params, timeout=30)
    if response.status_code != 200:
        raise RuntimeError(f"Google Maps API failed with status {response.status_code}")

    data = response.json()
    if data.get("status") != "OK":
        if data.get("status") == "ZERO_RESULTS":
            return []
        raise RuntimeError(f"Google Maps API returned error: {data.get('status')}")

    routes = []
    for index, route in enumerate(data.get("routes", [])):
        # Decode overview_polyline
        import polyline
        encoded_polyline = route["overview_polyline"]["points"]
        coordinates = polyline.decode(encoded_polyline)
        route_points = [
            {"latitude": coord[0], "longitude": coord[1]}
            for coord in coordinates
        ]

        leg = route["legs"][0]
        distance_meters = leg["distance"]["value"]
        duration_seconds = leg["duration"]["value"]
        duration_in_traffic_seconds = leg.get("duration_in_traffic", {}).get("value", duration_seconds)

        summary_name = route.get("summary", "")
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