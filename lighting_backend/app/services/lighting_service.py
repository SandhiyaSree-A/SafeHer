import h5py
import numpy as np


BASE_PATH = (
    "HDFEOS/GRIDS/"
    "VIIRS_Grid_DNB_2d/"
    "Data Fields/"
)

NTL_PATH = (
    BASE_PATH +
    "DNB_BRDF-Corrected_NTL"
)

GAP_FILLED_PATH = (
    BASE_PATH +
    "Gap_Filled_DNB_BRDF-Corrected_NTL"
)

QUALITY_PATH = (
    BASE_PATH +
    "Mandatory_Quality_Flag"
)

LAT_PATH = BASE_PATH + "lat"

LON_PATH = BASE_PATH + "lon"


def get_nearest_index(values, target):

    return int(
        np.abs(values - target).argmin()
    )


def calculate_light_score(radiance):
    """
    Convert NASA radiance into a
    0–100 lighting score.
    """

    if radiance is None or radiance < 0:
        return 0.0

    # Logarithmic normalization
    score = (
        100 *
        np.log1p(radiance) /
        np.log1p(100)
    )

    return round(
        float(min(100, max(0, score))),
        2
    )


def get_light_density(
    file_path,
    latitude,
    longitude
):
    """
    Get nighttime light information
    from a NASA VNP46A2 file.
    """

    with h5py.File(file_path, "r") as file:

        latitudes = file[LAT_PATH][:]
        longitudes = file[LON_PATH][:]

        # -------------------------
        # CHECK TILE BOUNDARIES
        # -------------------------

        lat_min = float(latitudes.min())
        lat_max = float(latitudes.max())

        lon_min = float(longitudes.min())
        lon_max = float(longitudes.max())

        if not (
            lat_min <= latitude <= lat_max
        ):
            raise ValueError(
                "Latitude is outside this NASA tile"
            )

        if not (
            lon_min <= longitude <= lon_max
        ):
            raise ValueError(
                "Longitude is outside this NASA tile"
            )

        # -------------------------
        # FIND PIXEL
        # -------------------------

        row = get_nearest_index(
            latitudes,
            latitude
        )

        column = get_nearest_index(
            longitudes,
            longitude
        )

        # -------------------------
        # READ VALUES
        # -------------------------

        direct_value = float(
            file[NTL_PATH][row, column]
        )

        gap_filled_value = float(
            file[GAP_FILLED_PATH][row, column]
        )

        quality_flag = int(
            file[QUALITY_PATH][row, column]
        )

        fill_value = float(
            file[NTL_PATH].attrs[
                "_FillValue"
            ][0]
        )

        # -------------------------
        # SELECT BEST VALUE
        # -------------------------

        if (
            direct_value != fill_value
            and direct_value >= 0
            and quality_flag == 0
        ):

            radiance = direct_value

            source = "direct"

            confidence = "high"

        elif gap_filled_value >= 0:

            radiance = gap_filled_value

            source = "gap_filled"

            confidence = "medium"

        else:

            radiance = None

            source = "unavailable"

            confidence = "none"

        # -------------------------
        # CALCULATE SCORE
        # -------------------------

        light_score = calculate_light_score(
            radiance
        )

        return {

            "latitude": latitude,

            "longitude": longitude,

            "pixel_row": row,

            "pixel_column": column,

            "direct_radiance":
                direct_value,

            "gap_filled_radiance":
                gap_filled_value,

            "selected_radiance":
                radiance,

            "data_source":
                source,

            "confidence":
                confidence,

            "quality_flag":
                quality_flag,

            "light_score":
                light_score
        }