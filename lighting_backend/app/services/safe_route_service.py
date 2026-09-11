from app.services.route_sampling_service import (
    sample_route_by_distance
)

from app.services.route_lighting_service import (
    analyze_route_lighting
)


def analyze_routes(
    routes,
    interval_meters=300
):
    """
    Analyze lighting for every available route
    and select the safest route.
    """

    if not routes:

        raise ValueError(
            "No routes available"
        )

    route_results = []

    print(
        "\n================================="
    )

    print(
        "ANALYZING ALL AVAILABLE ROUTES"
    )

    print(
        "================================="
    )

    # -----------------------------------
    # ANALYZE EVERY ROUTE
    # -----------------------------------

    for route in routes:

        route_id = route["route_id"]

        print(
            f"\n\n===== ANALYZING ROUTE "
            f"{route_id} ====="
        )

        # -------------------------------
        # SAMPLE ROUTE
        # -------------------------------

        sampled_points = (
            sample_route_by_distance(
                route["coordinates"],
                interval_meters
            )
        )

        print(
            "\nSampled Points:",
            len(sampled_points)
        )

        # -------------------------------
        # ANALYZE LIGHTING
        # -------------------------------

        lighting_result = (
            analyze_route_lighting(
                sampled_points
            )
        )

        # -------------------------------
        # STORE COMPLETE RESULT
        # -------------------------------

        route_results.append({

            "route_id":
                route_id,

            "distance_meters":
                route["distance_meters"],

            "duration_seconds":
                route["duration_seconds"],

            # ✅ ORIGINAL OSRM ROUTE
            # Used by frontend to draw route
            "coordinates":
                route["coordinates"],

            # ✅ SAMPLED POINTS
            # Used for NASA lighting analysis
            "sampled_points":
                sampled_points,

            # ✅ LIGHTING ANALYSIS
            "lighting":
                lighting_result
        })

    # -----------------------------------
    # FIND BEST-LIT ROUTE
    # -----------------------------------

    safest_route = max(

        route_results,

        key=lambda route:
        route["lighting"][
            "average_light_score"
        ]
    )

    # -----------------------------------
    # RETURN COMPLETE RESULT
    # -----------------------------------

    return {

        "total_routes":
            len(route_results),

        "safest_route_id":
            safest_route["route_id"],

        "safest_route":
            safest_route,

        "routes":
            route_results
    }