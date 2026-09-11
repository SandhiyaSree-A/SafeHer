from app.services.location_lighting_service import (
    get_location_lighting
)


latitude = 13.0827
longitude = 80.2707


result = get_location_lighting(
    latitude,
    longitude
)


print(
    "\n===== FINAL SAFEHER LIGHTING RESULT =====\n"
)


for key, value in result.items():

    print(
        f"{key}: {value}"
    )