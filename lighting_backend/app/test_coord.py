import h5py


file_path = (
    "data/nasa/"
    "VNP46A2.A2026242.h26v07.002.2026250101006.h5"
)


BASE_PATH = (
    "HDFEOS/GRIDS/"
    "VIIRS_Grid_DNB_2d/"
    "Data Fields/"
)


with h5py.File(file_path, "r") as file:

    latitudes = file[BASE_PATH + "lat"][:]
    longitudes = file[BASE_PATH + "lon"][:]

    print("\n===== LATITUDE INFORMATION =====")

    print("First:", latitudes[0])
    print("Last:", latitudes[-1])
    print("Minimum:", latitudes.min())
    print("Maximum:", latitudes.max())

    print("\n===== LONGITUDE INFORMATION =====")

    print("First:", longitudes[0])
    print("Last:", longitudes[-1])
    print("Minimum:", longitudes.min())
    print("Maximum:", longitudes.max())

    print("\n===== SAMPLE VALUES =====")

    print("\nLatitude samples:")
    print(latitudes[0:10])

    print("\nLongitude samples:")
    print(longitudes[0:10])