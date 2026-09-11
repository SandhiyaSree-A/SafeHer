def calculate_lighting_safety_score(
    average_light_score,
    darkest_point,
    dark_stretch_analysis
):
    """
    Calculate the final route lighting
    safety score.

    The score considers:

    1. Average route lighting
    2. Darkest point on the route
    3. Consecutive dark stretches
    """

    # ----------------------------------
    # AVERAGE LIGHTING
    # Weight: 50%
    # ----------------------------------

    average_component = (
        average_light_score * 0.50
    )


    # ----------------------------------
    # DARKEST POINT
    # Weight: 30%
    # ----------------------------------

    if darkest_point:

        darkest_score = (
            darkest_point["light_score"]
        )

    else:

        darkest_score = 0


    darkest_component = (
        darkest_score * 0.30
    )


    # ----------------------------------
    # DARK STRETCH SAFETY
    # Weight: 20%
    # ----------------------------------

    total_dark_stretches = (
        dark_stretch_analysis[
            "total_dark_stretches"
        ]
    )


    total_dark_points = (
        dark_stretch_analysis[
            "total_dark_points"
        ]
    )


    # Start with maximum score

    stretch_score = 100


    # Penalize dark stretches

    stretch_score -= (
        total_dark_stretches * 10
    )


    # Penalize individual dark points

    stretch_score -= (
        total_dark_points * 3
    )


    # Keep between 0 and 100

    stretch_score = max(
        0,
        min(
            100,
            stretch_score
        )
    )


    stretch_component = (
        stretch_score * 0.20
    )


    # ----------------------------------
    # FINAL SCORE
    # ----------------------------------

    final_score = (

        average_component

        + darkest_component

        + stretch_component
    )


    return {

        "lighting_safety_score":
            round(final_score, 2),

        "average_component":
            round(average_component, 2),

        "darkest_component":
            round(darkest_component, 2),

        "dark_stretch_component":
            round(stretch_component, 2),

        "dark_stretch_score":
            round(stretch_score, 2)
    }