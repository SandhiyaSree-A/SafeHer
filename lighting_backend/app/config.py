
import os
from dotenv import load_dotenv

load_dotenv()

EARTHDATA_TOKEN = os.getenv("EARTHDATA_TOKEN")

NASA_DATA_DIR = os.getenv("NASA_DATA_DIR", "data/nasa")

WORLDPOP_API_KEY = os.getenv("WORLDPOP_API_KEY", "")
WORLDPOP_BASE_URL = os.getenv("WORLDPOP_BASE_URL", "https://api.worldpop.org/v1/services/stats")

OVERPASS_BASE_URL = os.getenv("OVERPASS_BASE_URL", "http://overpass-api.de/api/interpreter")

ITD_DATA_DIR = os.getenv("ITD_DATA_DIR", "data/itd")
STREETSCOPE_DATA_DIR = os.getenv("STREETSCOPE_DATA_DIR", "data/streetscope")

os.makedirs(NASA_DATA_DIR, exist_ok=True)
os.makedirs(ITD_DATA_DIR, exist_ok=True)
os.makedirs(STREETSCOPE_DATA_DIR, exist_ok=True)