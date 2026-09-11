from app.services.routing_service import (
    get_routes
)

from app.services.safe_route_service import (
    analyze_routes
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
# GET AVAILABLE ROUTES
# --------------------------------

routes = get_routes(

    source_lat,
    source_lon,

    destination_lat,
    destination_lon
)


print(
    "\nNumber of routes found:",
    len(routes)
)


# --------------------------------
# ANALYZE ROUTES
# --------------------------------

result = analyze_routes(
    routes,
    interval_meters=300
)


# --------------------------------
# FINAL RESULTS
# --------------------------------

print(
    "\n\n================================="
)

print(
    "SAFEHER ROUTE ANALYSIS RESULT"
)

print(
    "================================="
)


print(
    "\nTotal Routes:",
    result["total_routes"]
)


# --------------------------------
# PRINT EACH ROUTE
# --------------------------------

for route in result["routes"]:

    print(
        f"\n===== ROUTE "
        f"{route['route_id']} ====="
    )


    # --------------------------------
    # DARK STRETCH ANALYSIS
    # --------------------------------

    dark_analysis = (
        route["lighting"][
            "dark_stretch_analysis"
        ]
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
        "Lighting Score:",
        route["lighting"][
            "average_light_score"
        ]
    )


    print(
        "Dark Points:",
        dark_analysis[
            "total_dark_points"
        ]
    )


    print(
        "Dark Stretches:",
        dark_analysis[
            "total_dark_stretches"
        ]
    )


    # --------------------------------
    # DARKEST POINT
    # --------------------------------

    darkest = (
        route["lighting"][
            "darkest_point"
        ]
    )


    if darkest:

        print(
            "Darkest Point Score:",
            darkest["light_score"]
        )


# --------------------------------
# SAFEST ROUTE
# --------------------------------

safest = result["safest_route"]


print(
    "\n\n🏆 SAFEST ROUTE"
)


print(
    "Route ID:",
    safest["route_id"]
)


print(
    "Lighting Score:",
    safest["lighting"][
        "average_light_score"
    ]
)

print(
    "Lighting Safety Score:",
    route["lighting"][
        "lighting_safety"
    ][
        "lighting_safety_score"
    ]
)

lighting_safety = (
    route["lighting"][
        "lighting_safety"
    ]
)


print(
    "Average Lighting Component:",
    lighting_safety[
        "average_component"
    ]
)


print(
    "Darkest Point Component:",
    lighting_safety[
        "darkest_component"
    ]
)


print(
    "Dark Stretch Component:",
    lighting_safety[
        "dark_stretch_component"
    ]
)

print(
    "Distance:",
    round(
        safest["distance_meters"] / 1000,
        2
    ),
    "km"
)