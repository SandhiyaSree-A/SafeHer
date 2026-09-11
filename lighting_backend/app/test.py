
from app.config import EARTHDATA_TOKEN

if EARTHDATA_TOKEN:
    print("NASA Earthdata token loaded successfully ✅")
else:
    print("NASA Earthdata token NOT found ❌")