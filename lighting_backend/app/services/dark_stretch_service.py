def analyze_dark_stretches(
    point_results,
    dark_threshold=50
):
    """
    Detect dark points and consecutive
    dark stretches along a route.

    Parameters:
        point_results:
            Lighting results from route analysis

        dark_threshold:
            Points below this light score
            are considered dark.
    """

    dark_points = []

    dark_stretches = []

    current_stretch = []


    # ----------------------------------
    # CHECK EVERY POINT
    # ----------------------------------

    for point in point_results:

        light_score = point.get(
            "light_score",
            0
        )

        # ------------------------------
        # DARK POINT
        # ------------------------------

        if light_score < dark_threshold:

            dark_points.append(
                point
            )

            current_stretch.append(
                point
            )

        else:

            # --------------------------
            # END OF DARK STRETCH
            # --------------------------

            if current_stretch:

                dark_stretches.append(
                    current_stretch
                )

                current_stretch = []


    # ----------------------------------
    # HANDLE FINAL STRETCH
    # ----------------------------------

    if current_stretch:

        dark_stretches.append(
            current_stretch
        )


    # ----------------------------------
    # FORMAT RESULTS
    # ----------------------------------

    formatted_stretches = []

    for stretch in dark_stretches:

        formatted_stretches.append({

            "start_point":
                stretch[0]["point_number"],

            "end_point":
                stretch[-1]["point_number"],

            "number_of_dark_points":
                len(stretch),

            "minimum_light_score":
                min(
                    point["light_score"]
                    for point in stretch
                ),

            "points":
                stretch
        })


    return {

        "dark_threshold":
            dark_threshold,

        "total_dark_points":
            len(dark_points),

        "total_dark_stretches":
            len(formatted_stretches),

        "dark_stretches":
            formatted_stretches
    }