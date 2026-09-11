import h5py


file_path = "data/nasa/test_nasa_file.h5"


def print_structure(name, obj):

    print(name)

    if isinstance(obj, h5py.Dataset):

        print("    Shape:", obj.shape)
        print("    Data type:", obj.dtype)

        print("    Attributes:")

        for key, value in obj.attrs.items():
            print(f"        {key}: {value}")

        print()


print("\nOpening NASA Black Marble file...\n")


with h5py.File(file_path, "r") as file:

    print("=" * 60)
    print("NASA HDF5 FILE STRUCTURE")
    print("=" * 60)

    file.visititems(print_structure)