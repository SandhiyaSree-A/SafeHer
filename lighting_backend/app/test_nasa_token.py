import requests

from app.config import EARTHDATA_TOKEN


url = (
    "https://ladsweb.modaps.eosdis.nasa.gov/"
    "archive/README"
)

headers = {
    "Authorization": f"Bearer {EARTHDATA_TOKEN}",
    "User-Agent": "SafeHer-Lighting-Backend/1.0"
}


response = requests.get(
    url,
    headers=headers,
    allow_redirects=False,
    timeout=30
)


print("Status Code:", response.status_code)

print("\nLocation:")
print(response.headers.get("Location"))

print("\nContent-Type:")
print(response.headers.get("Content-Type"))

print("\nFirst 200 bytes:")
print(response.content[:200])