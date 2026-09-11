from app.services.nasa_downloader import (
    get_latest_nasa_file
)


# Test location: Chennai
latitude = 13.0827
longitude = 80.2707


file_path = get_latest_nasa_file(
    latitude,
    longitude
)


print("\nNASA FILE READY:")
print(file_path)