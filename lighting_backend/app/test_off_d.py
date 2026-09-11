import os
import shutil
import ssl

from urllib.request import urlopen, Request

from app.config import EARTHDATA_TOKEN


USERAGENT = "SafeHer-Lighting-Backend/1.0"


url = (
    "https://ladsweb.modaps.eosdis.nasa.gov/"
    "archive/allData/5200/VNP46A2/"
    "2026/242/"
    "VNP46A2.A2026242.h25v07.002.2026250102106.h5"
)


headers = {
    "user-agent": USERAGENT,
    "Authorization": f"Bearer {EARTHDATA_TOKEN}"
}


# Same TLS approach used in the NASA official script
context = ssl.SSLContext(ssl.PROTOCOL_TLSv1_2)


output_path = (
    "data/nasa/test_nasa_file.h5"
)


os.makedirs(
    "data/nasa",
    exist_ok=True
)


print("Requesting NASA file...\n")


request = Request(
    url,
    headers=headers
)


try:

    response = urlopen(
        request,
        context=context
    )

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


    with open(output_path, "wb") as output_file:

        shutil.copyfileobj(
            response,
            output_file
        )


    print("\nDownload completed!")

    print(
        "File size:",
        os.path.getsize(output_path),
        "bytes"
    )


except Exception as error:

    print("\nDOWNLOAD ERROR:")

    print(error)