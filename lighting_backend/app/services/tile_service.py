import math


def lat_lon_to_tile(latitude: float, longitude: float):
    """
    Convert latitude and longitude to the
    VNP46A2 10-degree tile.

    Tile structure:

    Longitude:
    h00 = -180 to -170
    h01 = -170 to -160
    ...
    h25 = 70 to 80
    h26 = 80 to 90

    Latitude:
    v00 = 90 to 80
    v01 = 80 to 70
    ...
    v07 = 20 to 10
    """

    # Validate coordinates

    if not -90 <= latitude <= 90:
        raise ValueError(
            "Latitude must be between -90 and 90"
        )

    if not -180 <= longitude <= 180:
        raise ValueError(
            "Longitude must be between -180 and 180"
        )

    # ------------------------------------
    # HORIZONTAL TILE
    # ------------------------------------

    h = math.floor(
        (longitude + 180) / 10
    )

    # ------------------------------------
    # VERTICAL TILE
    # ------------------------------------

    v = math.floor(
        (90 - latitude) / 10
    )

    # Handle boundary values

    h = min(max(h, 0), 35)
    v = min(max(v, 0), 17)

    return h, v


def get_tile_name(
    latitude: float,
    longitude: float
):
    """
    Return NASA VNP46A2 tile name.

    Example:

    Chennai:
    13.0827, 80.2707

    Returns:
    h26v07
    """

    h, v = lat_lon_to_tile(
        latitude,
        longitude
    )

    return f"h{h:02d}v{v:02d}"