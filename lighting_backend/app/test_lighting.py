from app.services.lighting_service import get_light_density


file_path = (
    "data/nasa/"
    "VNP46A2.A2026242.h26v07.002.2026250101006.h5"
)


latitude = 13.0827
longitude = 80.2707


result = get_light_density(
    file_path,
    latitude,
    longitude
)


print("\n===== NASA NIGHTTIME LIGHT RESULT =====\n")

for key, value in result.items():
    print(f"{key}: {value}")