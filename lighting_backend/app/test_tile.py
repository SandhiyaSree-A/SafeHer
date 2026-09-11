from app.services.tile_service import (
    get_tile_name
)


# Chennai

latitude = 13.0827
longitude = 80.2707


tile = get_tile_name(
    latitude,
    longitude
)


print("Latitude:", latitude)
print("Longitude:", longitude)

print("\nNASA Tile:")
print(tile)