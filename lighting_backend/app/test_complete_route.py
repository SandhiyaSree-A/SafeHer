from app.services.routing_service import (
    get_routes
)

from app.services.route_sampling_service import (
    sample_route_points
)

from app.services.route_lighting_service import (
    analyze_route_lighting
)


# --------------------------------
# SOURCE
# --------------------------------

source_lat = 13.0827
source_lon = 80.2707


# --------------------------------
# DESTINATION
# --------------------------------

destination_lat = 13.0500
destination_lon = 80.2824


# --------------------------------
# GET ROUTES
# --------------------------------

routes = get_routes(

    source_lat,
    source_lon,

    destination_lat,
    destination_lon
)


# --------------------------------
# SELECT FIRST ROUTE
# --------------------------------

route = routes[0]


# --------------------------------
# SAMPLE ROUTE
# --------------------------------

sampled_points = sample_route_points(

    route["coordinates"],

    sample_count=15
)


# --------------------------------
# ANALYZE LIGHTING
# --------------------------------

result = analyze_route_lighting(
    sampled_points
)


# --------------------------------
# PRINT RESULTS
# --------------------------------

print(
    "\n\n================================="
)

print(
    "FINAL ROUTE LIGHTING RESULT"
)

print(
    "================================="
)


print(
    "\nTotal Sample Points:",
    result["total_points"]
)


print(
    "Analyzed Points:",
    result["analyzed_points"]
)


print(
    "\nAVERAGE LIGHT SCORE:",
    result["average_light_score"]
)


# --------------------------------
# DARKEST POINT
# --------------------------------

darkest = result["darkest_point"]


if darkest:

    print(
        "\nDARKEST POINT"
    )

    print(
        "Point Number:",
        darkest["point_number"]
    )

    print(
        "Latitude:",
        darkest["latitude"]
    )

    print(
        "Longitude:",
        darkest["longitude"]
    )

    print(
        "Light Score:",
        darkest["light_score"]
    )


# --------------------------------
# INDIVIDUAL POINT SCORES
# --------------------------------

print(
    "\n===== INDIVIDUAL POINT SCORES ====="
)


for point in result["points"]:

    print(
        f"\nPoint {point['point_number']}"
    )

    print(
        "Location:",
        point["latitude"],
        point["longitude"]
    )

    print(
        "Radiance:",
        point["selected_radiance"]
    )

    print(
        "Light Score:",
        point["light_score"]
    )

    print(
        "Source:",
        point["data_source"]
    )