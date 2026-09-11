
import os
from dotenv import load_dotenv

load_dotenv()

EARTHDATA_TOKEN = os.getenv("EARTHDATA_TOKEN")

NASA_DATA_DIR = "data/nasa"

os.makedirs(NASA_DATA_DIR, exist_ok=True)