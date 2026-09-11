
import os
import re
import ssl
import shutil

from datetime import datetime, timedelta

import requests

from urllib.request import urlopen, Request

from app.config import EARTHDATA_TOKEN, NASA_DATA_DIR
from app.services.tile_service import get_tile_name


BASE_URL = (
    "https://ladsweb.modaps.eosdis.nasa.gov/"
    "archive/allData/5200/VNP46A2"
)


def get_headers():

    if not EARTHDATA_TOKEN:

        raise RuntimeError(
            "EARTHDATA_TOKEN is missing from .env"
        )


    return {

        "Authorization":
            f"Bearer {EARTHDATA_TOKEN}",

        "User-Agent":
            "SafeHer-Lighting-Backend/1.0"
    }

def get_day_of_year(date):
    """
    Convert date to NASA year/day-of-year format.

    Example:
    2026-09-07 -> 2026 / 250
    """

    return date.year, date.timetuple().tm_yday


def get_archive_url(date):
    """
    Create NASA archive URL for a specific day.
    """

    year, day = get_day_of_year(date)

    return (
        f"{BASE_URL}/"
        f"{year}/"
        f"{day:03d}/"
    )


def find_tile_file(latitude, longitude, date):
    """
    Find the correct NASA VNP46A2 file
    for a location and date.
    """

    tile_name = get_tile_name(
        latitude,
        longitude
    )

    archive_url = get_archive_url(date)

    print("\nChecking NASA archive:")
    print(archive_url)

    print("Looking for tile:")
    print(tile_name)

    response = requests.get(
        archive_url,
        headers=get_headers(),
        timeout=30
    )

    if response.status_code != 200:

        print(
            "Archive unavailable:",
            response.status_code
        )

        return None

    pattern = (
        r'href="([^"]*VNP46A2[^"]*'
        + re.escape(tile_name)
        + r'[^"]*\.h5)"'
    )

    matches = re.findall(
        pattern,
        response.text,
        re.IGNORECASE
    )

    if not matches:

        print(
            "No matching file found for",
            tile_name
        )

        return None

    filename = matches[0].split("/")[-1]

    print("\nFound NASA file:")
    print(filename)

    # IMPORTANT:
    # Use the normal LAADS archive URL
    # instead of the API v2 URL.

    download_url = (
        archive_url +
        filename
    )

    print("\nDownload URL:")
    print(download_url)

    return download_url

def is_valid_hdf5(file_path):
    """
    Check whether a file has the official
    HDF5 file signature.
    """

    HDF5_SIGNATURE = b"\x89HDF\r\n\x1a\n"

    try:
        with open(file_path, "rb") as file:
            signature = file.read(8)

        return signature == HDF5_SIGNATURE

    except Exception:
        return False


def download_file(url):
    """
    Download NASA HDF5 file using the official
    NASA urllib download approach.

    The downloaded file is validated using
    the HDF5 signature.
    """

    filename = url.split("/")[-1]

    local_path = os.path.join(
        NASA_DATA_DIR,
        filename
    )

    # ---------------------------------
    # CREATE NASA DATA DIRECTORY
    # ---------------------------------

    os.makedirs(
        NASA_DATA_DIR,
        exist_ok=True
    )

    # ---------------------------------
    # CHECK CACHE
    # ---------------------------------

    if os.path.exists(local_path):

        if is_valid_hdf5(local_path):

            print(
                "\nUsing valid cached NASA file:"
            )

            print(local_path)

            return local_path

        else:

            print(
                "\nInvalid cached file detected."
            )

            print(
                "Deleting invalid cache..."
            )

            os.remove(local_path)

    # ---------------------------------
    # DOWNLOAD USING NASA'S
    # OFFICIAL urllib METHOD
    # ---------------------------------

    print("\nDownloading NASA data...")
    print(filename)

    headers = {

        "user-agent":
            "SafeHer-Lighting-Backend/1.0",

        "Authorization":
            f"Bearer {EARTHDATA_TOKEN}"
    }

    request = Request(
        url,
        headers=headers
    )

    # Same TLS approach used in
    # NASA's official download script

    context = ssl.SSLContext(
        ssl.PROTOCOL_TLSv1_2
    )

    temp_path = (
        local_path + ".download"
    )

    try:

        response = urlopen(
            request,
            context=context,
            timeout=180
        )

        print("\nNASA Response Information:")

        print(
            "Content-Type:",
            response.headers.get(
                "Content-Type"
            )
        )

        print(
            "Final URL:",
            response.geturl()
        )

        # ---------------------------------
        # SAVE FILE
        # ---------------------------------

        with open(
            temp_path,
            "wb"
        ) as output_file:

            shutil.copyfileobj(
                response,
                output_file
            )

    except Exception as error:

        # Remove incomplete file

        if os.path.exists(temp_path):

            os.remove(temp_path)

        raise RuntimeError(
            f"NASA download failed: {error}"
        )

    # ---------------------------------
    # VALIDATE HDF5 FILE
    # ---------------------------------

    if not is_valid_hdf5(temp_path):

        print(
            "\n❌ Download is NOT a valid HDF5 file."
        )

        with open(
            temp_path,
            "rb"
        ) as file:

            preview = file.read(200)

        print(
            "\nFirst bytes received:"
        )

        print(preview)

        os.remove(temp_path)

        raise RuntimeError(
            "NASA download failed HDF5 validation."
        )

    # ---------------------------------
    # MOVE VALID FILE TO CACHE
    # ---------------------------------

    os.replace(
        temp_path,
        local_path
    )

    print(
        "\n✅ Valid NASA HDF5 download completed!"
    )

    print(
        "File size:",
        os.path.getsize(local_path),
        "bytes"
    )

    return local_path

def get_latest_nasa_file(
    latitude,
    longitude,
    max_days_back=30
):
    """
    Search backwards until an available
    NASA VNP46A2 tile is found.

    NASA data is not guaranteed to be available
    immediately for the current day.
    """

    today = datetime.utcnow().date()

    for days_back in range(max_days_back):

        date = (
            today -
            timedelta(days=days_back)
        )

        print(
            f"\nTrying date: {date}"
        )

        file_url = find_tile_file(
            latitude,
            longitude,
            date
        )

        if file_url:

            return download_file(
                file_url
            )

    raise RuntimeError(
        "No NASA VNP46A2 data found "
        f"in the last {max_days_back} days."
    )

def get_download_url(filename):
    """
    Create the NASA LAADS API v2 URL
    for downloading a specific archive file.
    """

    archive_path = (
        "allData/5200/VNP46A2/"
        f"{filename.split('.A')[1][:4]}/"
    )

    return None


def create_nasa_session():
    """
    Create a persistent NASA/LAADS session.

    The session keeps cookies while requests move
    between LAADS and Earthdata authentication.
    """

    if not EARTHDATA_TOKEN:
        raise RuntimeError(
            "EARTHDATA_TOKEN is missing from .env"
        )

    session = requests.Session()

    session.headers.update({
        "Authorization": f"Bearer {EARTHDATA_TOKEN}",
        "User-Agent": "SafeHer-Lighting-Backend/1.0",
        "X-Requested-With": "XMLHttpRequest"
    })

    return session