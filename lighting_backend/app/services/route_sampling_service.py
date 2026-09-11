import math


def haversine_distance(
    lat1,
    lon1,
    lat2,
    lon2
):
    """
    Calculate distance between two GPS points
    in meters.
    """

    earth_radius = 6371000

    lat1_rad = math.radians(lat1)
    lon1_rad = math.radians(lon1)

    lat2_rad = math.radians(lat2)
    lon2_rad = math.radians(lon2)

    lat_difference = (
        lat2_rad - lat1_rad
    )

    lon_difference = (
        lon2_rad - lon1_rad
    )

    a = (
        math.sin(lat_difference / 2) ** 2
        +
        math.cos(lat1_rad)
        *
        math.cos(lat2_rad)
        *
        math.sin(lon_difference / 2) ** 2
    )

    c = (
        2
        *
        math.atan2(
            math.sqrt(a),
            math.sqrt(1 - a)
        )
    )

    return earth_radius * c


def sample_route_by_distance(
    coordinates,
    interval_meters=300
):
    """
    Sample route points based on actual
    geographical distance.

    Example:
    Select approximately one point
    every 300 meters.
    """

    if not coordinates:

        return []

    if len(coordinates) == 1:

        return coordinates

    sampled_points = []

    # Always include source

    sampled_points.append(
        coordinates[0]
    )

    distance_since_last_sample = 0

    previous_point = coordinates[0]

    # --------------------------------
    # WALK THROUGH THE ROUTE
    # --------------------------------

    for current_point in coordinates[1:]:

        distance = haversine_distance(

            previous_point["latitude"],
            previous_point["longitude"],

            current_point["latitude"],
            current_point["longitude"]
        )

        distance_since_last_sample += distance

        # --------------------------------
        # SAMPLE POINT
        # --------------------------------

        if (
            distance_since_last_sample
            >= interval_meters
        ):

            sampled_points.append(
                current_point
            )

            distance_since_last_sample = 0

        previous_point = current_point

    # --------------------------------
    # ALWAYS INCLUDE DESTINATION
    # --------------------------------

    destination = coordinates[-1]

    last_sample = sampled_points[-1]

    if (

        last_sample["latitude"]
        != destination["latitude"]

        or

        last_sample["longitude"]
        != destination["longitude"]
    ):

        sampled_points.append(
            destination
        )

    return sampled_points