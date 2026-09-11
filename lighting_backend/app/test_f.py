
from pathlib import Path


file_path = Path(
    "data/nasa/VNP46A2.A2026242.h25v07.002.2026250102106.h5"
)


print("File exists:", file_path.exists())

print("File size:", file_path.stat().st_size, "bytes")


with open(file_path, "rb") as file:

    first_bytes = file.read(200)


print("\nFirst 200 bytes:\n")

print(first_bytes)