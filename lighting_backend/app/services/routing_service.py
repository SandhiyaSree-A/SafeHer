import requests


OSRM_BASE_URL = (
    "https://router.project-osrm.org"
)


def get_routes(
    source_lat,
    source_lon,
    destination_lat,
    destination_lon
):
    """
    Get alternative driving routes
    between source and destination.
    """

    url = (
        f"{OSRM_BASE_URL}/route/v1/driving/"
        f"{source_lon},{source_lat};"
        f"{destination_lon},{destination_lat}"
    )

    params = {
        "overview": "full",
        "geometries": "geojson",
        "alternatives": "3",
        "steps": "false"
    }

    print("\nRequesting routes from OSRM...")

    response = requests.get(
        url,
        params=params,
        timeout=30
    )

    if response.status_code != 200:

        raise RuntimeError(
            f"OSRM routing failed: "
            f"{response.status_code}"
        )

    data = response.json()

    if data.get("code") != "Ok":

        raise RuntimeError(
            f"OSRM error: {data}"
        )

    routes = []

    for index, route in enumerate(
        data["routes"]
    ):

        coordinates = (
            route["geometry"]["coordinates"]
        )

        # GeoJSON gives:
        # [longitude, latitude]

        route_points = [

            {
                "latitude": coordinate[1],
                "longitude": coordinate[0]
            }

            for coordinate in coordinates
        ]

        routes.append({

            "route_id": index + 1,

            "distance_meters":
                route["distance"],

            "duration_seconds":
                route["duration"],

            "coordinates":
                route_points
        })

    return routes