import os

from app.services.nasa_downloader import (
    create_nasa_session
)


url = (
    "https://ladsweb.modaps.eosdis.nasa.gov/"
    "archive/allData/5200/VNP46A2/"
    "2026/242/"
    "VNP46A2.A2026242.h25v07.002.2026250102106.h5"
)


session = create_nasa_session()


print("Requesting NASA HDF5 file...\n")

response = session.get(
    url,
    stream=True,
    allow_redirects=True,
    timeout=180
)


print("Status:", response.status_code)

print(
    "Content-Type:",
    response.headers.get("Content-Type")
)

print(
    "Final URL:",
    response.url
)


first_bytes = response.raw.read(8)

print("\nFirst 8 bytes:")
print(first_bytes)


HDF5_SIGNATURE = b"\x89HDF\r\n\x1a\n"


if first_bytes == HDF5_SIGNATURE:

    print("\n✅ SUCCESS!")

    print(
        "This is a real NASA HDF5 file."
    )

else:

    print("\n❌ Not an HDF5 file.")

    print("\nResponse preview:")

    remaining = response.raw.read(200)

    print(first_bytes + remaining)