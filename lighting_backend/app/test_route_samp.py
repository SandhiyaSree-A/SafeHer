from app.services.routing_service import (
    get_routes
)

from app.services.route_sampling_service import (
    sample_route_points
)


# Chennai Central area

source_lat = 13.0827
source_lon = 80.2707


# Destination

destination_lat = 13.0500
destination_lon = 80.2824


# ----------------------------
# GET ROUTES
# ----------------------------

routes = get_routes(

    source_lat,
    source_lon,

    destination_lat,
    destination_lon
)


# Use the first route

route = routes[0]


coordinates = route[
    "coordinates"
]


print(
    "\nTotal route coordinates:",
    len(coordinates)
)


# ----------------------------
# SAMPLE ROUTE
# ----------------------------

sampled_points = sample_route_points(
    coordinates,
    sample_count=15
)


print(
    "\n===== SAMPLED ROUTE POINTS =====\n"
)


for index, point in enumerate(
    sampled_points,
    start=1
):

    print(
        f"Point {index}"
    )

    print(
        "Latitude:",
        point["latitude"]
    )

    print(
        "Longitude:",
        point["longitude"]
    )

    print()