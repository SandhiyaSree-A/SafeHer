from app.services.nasa_downloader import (
    get_latest_nasa_file
)

from app.services.lighting_service import (
    get_light_density
)


def get_location_lighting(
    latitude,
    longitude
):
    """
    Complete NASA nighttime lighting pipeline.

    Given a latitude and longitude:

    1. Finds the correct NASA tile
    2. Downloads the latest available NASA data
    3. Uses cached data when available
    4. Reads nighttime light density
    5. Returns a lighting safety score
    """

    print(
        "\n================================="
    )

    print(
        "SAFEHER LIGHTING ANALYSIS"
    )

    print(
        "================================="
    )

    print(
        f"\nLocation:"
    )

    print(
        f"Latitude: {latitude}"
    )

    print(
        f"Longitude: {longitude}"
    )

    # -----------------------------
    # GET LATEST NASA FILE
    # -----------------------------

    nasa_file = get_latest_nasa_file(
        latitude,
        longitude
    )

    # -----------------------------
    # ANALYZE LIGHTING
    # -----------------------------

    lighting_result = get_light_density(
        nasa_file,
        latitude,
        longitude
    )

    # Add NASA file information

    lighting_result[
        "nasa_file"
    ] = nasa_file

    return lighting_result