import os
import logging
import json
from app.config import ITD_DATA_DIR
from math import radians, cos, sin, asin, sqrt

logger = logging.getLogger(__name__)

# Basic in-memory cache for loaded ITD files
_itd_cache = None

def _haversine(lat1, lon1, lat2, lon2):
    R = 6372.8 
    dLat = radians(lat2 - lat1)
    dLon = radians(lon2 - lon1)
    lat1 = radians(lat1)
    lat2 = radians(lat2)
    a = sin(dLat/2)**2 + cos(lat1)*cos(lat2)*sin(dLon/2)**2
    c = 2*asin(sqrt(a))
    return R * c * 1000 # returns meters

def _load_itd_data():
    global _itd_cache
    if _itd_cache is not None:
        return _itd_cache
    
    _itd_cache = []
    try:
        # Load sample ITD data if it exists
        index_file = os.path.join(ITD_DATA_DIR, "traffic_index.json")
        if os.path.exists(index_file):
            with open(index_file, 'r') as f:
                _itd_cache = json.load(f)
    except Exception as e:
        logger.error(f"Failed to load ITD data: {e}")
        
    return _itd_cache

def get_traffic_factor(lat, lng, radius_meters=500):
    """
    Returns a traffic density/safety factor from the Indian Traffic Dataset.
    If no data is available for this coordinate, returns None.
    """
    data = _load_itd_data()
    if not data:
        return None # Unavailable
        
    # Find nearest data point
    nearest = None
    min_dist = float('inf')
    
    for point in data:
        dist = _haversine(lat, lng, point['lat'], point['lng'])
        if dist < min_dist:
            min_dist = dist
            nearest = point
            
    if nearest and min_dist <= radius_meters:
        return nearest.get('traffic_volume', 0)
        
    return None
