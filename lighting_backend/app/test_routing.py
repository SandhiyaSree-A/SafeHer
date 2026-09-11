from app.services.routing_service import (
    get_routes
)


# Example:
# Chennai Central → Marina Beach

source_lat = 13.0827
source_lon = 80.2707

destination_lat = 13.0500
destination_lon = 80.2824


routes = get_routes(

    source_lat,
    source_lon,

    destination_lat,
    destination_lon
)


print(
    "\n===== AVAILABLE ROUTES =====\n"
)


for route in routes:

    print(
        f"Route {route['route_id']}"
    )

    print(
        "Distance:",
        round(
            route["distance_meters"] / 1000,
            2
        ),
        "km"
    )

    print(
        "Duration:",
        round(
            route["duration_seconds"] / 60,
            2
        ),
        "minutes"
    )

    print(
        "Number of coordinates:",
        len(
            route["coordinates"]
        )
    )

    print()