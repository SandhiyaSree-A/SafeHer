import requests

OSRM_BASE_URL = "https://router.project-osrm.org"

def get_routes(
    source_lat: float,
    source_lon: float,
    destination_lat: float,
    destination_lon: float,
    mode: str = "driving"
):
    """
    Get alternative routes (driving, walking, bike) from OSRM
    with turn-by-turn step details and real street names.
    """
    osrm_profile = "driving"
    if mode in ["walking", "walk"]:
        osrm_profile = "foot"
    elif mode in ["bicycling", "bike"]:
        osrm_profile = "bike"

    url = (
        f"{OSRM_BASE_URL}/route/v1/{osrm_profile}/"
        f"{source_lon},{source_lat};"
        f"{destination_lon},{destination_lat}"
    )

    params = {
        "overview": "full",
        "geometries": "geojson",
        "alternatives": "true",
        "steps": "true"
    }

    print(f"\nRequesting real-time {mode} routes from OSRM...")

    response = requests.get(url, params=params, timeout=30)
    if response.status_code != 200:
        raise RuntimeError(f"OSRM routing failed with status {response.status_code}")

    data = response.json()
    if data.get("code") != "Ok":
        raise RuntimeError(f"OSRM returned error code: {data}")

    routes = []
    for index, route in enumerate(data.get("routes", [])):
        coordinates = route["geometry"]["coordinates"]
        route_points = [
            {"latitude": coord[1], "longitude": coord[0]}
            for coord in coordinates
        ]

        legs = route.get("legs", [])
        street_names = []
        turn_steps = []

        if legs:
            leg = legs[0]
            summary_name = leg.get("summary", "")
            if summary_name:
                street_names.append(summary_name)

            for step in leg.get("steps", []):
                step_name = step.get("name", "").strip()
                if step_name and step_name not in street_names:
                    street_names.append(step_name)

                maneuver = step.get("maneuver", {})
                maneuver_location = maneuver.get("location", [source_lon, source_lat])
                maneuver_type = maneuver.get("type", "turn")
                maneuver_modifier = maneuver.get("modifier", "")

                instruction = f"{maneuver_type.capitalize()} {maneuver_modifier}".strip()
                if step_name:
                    instruction += f" onto {step_name}"

                turn_steps.append({
                    "instruction": instruction,
                    "road_name": step_name or "Main Road",
                    "distance_meters": step.get("distance", 0.0),
                    "duration_seconds": step.get("duration", 0.0),
                    "start_lat": maneuver_location[1],
                    "start_lng": maneuver_location[0]
                })

        # Formulate real via route description from actual step street names
        unique_roads = [s for s in street_names if s]
        if len(unique_roads) >= 2:
            via_route_str = f"via {unique_roads[0]} / {unique_roads[1]}"
        elif len(unique_roads) == 1:
            via_route_str = f"via {unique_roads[0]}"
        else:
            via_route_str = f"via Primary Transport Corridor {index + 1}"

        routes.append({
            "route_id": index + 1,
            "transport_mode": mode,
            "distance_meters": route["distance"],
            "duration_seconds": route["duration"],
            "via_route": via_route_str,
            "street_names": unique_roads,
            "coordinates": route_points,
            "turn_steps": turn_steps
        })

    return routes