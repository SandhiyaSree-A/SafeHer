import os


file_path = (
    "data/nasa/test_nasa_file.h5"
)


print(
    "File size:",
    os.path.getsize(file_path),
    "bytes"
)


with open(file_path, "rb") as file:

    signature = file.read(8)


print(
    "First 8 bytes:",
    signature
)


if signature == b"\x89HDF\r\n\x1a\n":

    print(
        "\n✅ REAL NASA HDF5 FILE!"
    )

else:

    print(
        "\n❌ Not a valid HDF5 file"
    )