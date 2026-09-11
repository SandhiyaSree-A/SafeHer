from app.services.routing_service import (
    get_routes
)

from app.services.route_sampling_service import (
    sample_route_by_distance
)


source_lat = 13.0827
source_lon = 80.2707

destination_lat = 13.0500
destination_lon = 80.2824


# ------------------------------
# GET ROUTE
# ------------------------------

routes = get_routes(

    source_lat,
    source_lon,

    destination_lat,
    destination_lon
)


route = routes[0]


print(
    "\nTotal Route Coordinates:",
    len(route["coordinates"])
)


# ------------------------------
# DISTANCE SAMPLING
# ------------------------------

sampled_points = (
    sample_route_by_distance(

        route["coordinates"],

        interval_meters=300
    )
)


print(
    "\n===== DISTANCE SAMPLED POINTS ====="
)


print(
    "\nTotal Sampled Points:",
    len(sampled_points)
)


for index, point in enumerate(
    sampled_points,
    start=1
):

    print(
        f"\nPoint {index}"
    )

    print(
        "Latitude:",
        point["latitude"]
    )

    print(
        "Longitude:",
        point["longitude"]
    )