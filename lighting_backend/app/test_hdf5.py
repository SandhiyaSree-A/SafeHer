import h5py

from app.services.nasa_downloader import (
    get_latest_nasa_file
)


# Chennai test location
latitude = 13.0827
longitude = 80.2707


# Get the NASA file
file_path = get_latest_nasa_file(
    latitude,
    longitude
)


def print_structure(name, obj):
    """
    Print every group and dataset
    inside the NASA HDF5 file.
    """

    print(name)

    if isinstance(obj, h5py.Dataset):
        print("   Shape:", obj.shape)
        print("   Data type:", obj.dtype)


print("\nOpening NASA file:\n")
print(file_path)


with h5py.File(file_path, "r") as file:

    print("\n===== NASA HDF5 STRUCTURE =====\n")

    file.visititems(print_structure)