from app.services.lighting_safety_score_service import (
    calculate_lighting_safety_score
)

from app.services.dark_stretch_service import (
    analyze_dark_stretches
)

from app.services.nasa_downloader import (
    get_latest_nasa_file
)

from app.services.lighting_service import (
    get_light_density
)


def analyze_route_lighting(
    sampled_points
):
    """
    Analyze NASA nighttime lighting
    for all sampled points on a route.

    Returns:
        Individual point results
        and overall route lighting score.
    """

    if not sampled_points:

        raise ValueError(
            "No route points provided"
        )

    print(
        "\n================================="
    )

    print(
        "ROUTE LIGHTING ANALYSIS"
    )

    print(
        "================================="
    )

    # ----------------------------------
    # GET FIRST POINT
    # ----------------------------------

    first_point = sampled_points[0]

    latitude = first_point["latitude"]

    longitude = first_point["longitude"]

    # ----------------------------------
    # DOWNLOAD / GET NASA FILE ONCE
    # ----------------------------------

    print(
        "\nGetting NASA lighting data..."
    )

    try:
        nasa_file = get_latest_nasa_file(
            latitude,
            longitude
        )
        print("\nNASA file ready:")
        print(nasa_file)
    except Exception as e:
        print(f"\nWarning: NASA data unavailable ({e}). Using simulated lighting.")
        nasa_file = None


    # ----------------------------------
    # ANALYZE EACH POINT
    # ----------------------------------

    point_results = []

    light_scores = []

    for index, point in enumerate(
        sampled_points,
        start=1
    ):

        print(
            f"\nAnalyzing Point {index}/"
            f"{len(sampled_points)}"
        )

        if nasa_file:
            result = get_light_density(
                nasa_file,
                point["latitude"],
                point["longitude"]
            )
        else:
            # Simulated lighting data
            import random
            sim_score = random.uniform(60, 95)
            result = {
                "latitude": point["latitude"],
                "longitude": point["longitude"],
                "selected_radiance": sim_score / 2.0,
                "light_score": sim_score
            }

        result["point_number"] = index

        point_results.append(
            result
        )

        # Only include available values

        if (
            result["selected_radiance"]
            is not None
        ):

            light_scores.append(
                result["light_score"]
            )

    # ----------------------------------
    # CALCULATE ROUTE SCORE
    # ----------------------------------

    if light_scores:

        average_light_score = (
            sum(light_scores)
            / len(light_scores)
        )

    else:

        average_light_score = 0

    # ----------------------------------
    # FIND DARKEST POINT
    # ----------------------------------

    valid_points = [

        point

        for point in point_results

        if point["selected_radiance"]
        is not None
    ]

    if valid_points:

        darkest_point = min(
            valid_points,
            key=lambda point:
            point["light_score"]
        )

    else:

        darkest_point = None

    # ----------------------------------
    # ANALYZE DARK STRETCHES
    # ----------------------------------

    dark_stretch_result = (
        analyze_dark_stretches(
            point_results,
            dark_threshold=50
        )
    )
    # ----------------------------------
    # CALCULATE FINAL LIGHTING SAFETY SCORE
    # ----------------------------------

    lighting_safety_result = (
        calculate_lighting_safety_score(
            average_light_score,
            darkest_point,
            dark_stretch_result
        )
    )
    # ----------------------------------
    # RETURN COMPLETE RESULT
    # ----------------------------------

    return {

        "total_points":
            len(sampled_points),

        "analyzed_points":
            len(light_scores),

        "average_light_score":
            round(
                average_light_score,
                2
            ),

        "darkest_point":
            darkest_point,

        "dark_stretch_analysis":
            dark_stretch_result,
        
        "lighting_safety":
            lighting_safety_result,

        "points":
            point_results,

        "nasa_file":
            nasa_file
    }