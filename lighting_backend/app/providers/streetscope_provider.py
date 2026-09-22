import os
import logging
import json
from app.config import STREETSCOPE_DATA_DIR
from math import radians, cos, sin, asin, sqrt

logger = logging.getLogger(__name__)

_streetscope_cache = None

def _haversine(lat1, lon1, lat2, lon2):
    R = 6372.8 
    dLat = radians(lat2 - lat1)
    dLon = radians(lon2 - lon1)
    lat1 = radians(lat1)
    lat2 = radians(lat2)
    a = sin(dLat/2)**2 + cos(lat1)*cos(lat2)*sin(dLon/2)**2
    c = 2*asin(sqrt(a))
    return R * c * 1000

def _load_streetscope_data():
    global _streetscope_cache
    if _streetscope_cache is not None:
        return _streetscope_cache
    
    _streetscope_cache = []
    try:
        index_file = os.path.join(STREETSCOPE_DATA_DIR, "pedestrian_index.json")
        if os.path.exists(index_file):
            with open(index_file, 'r') as f:
                _streetscope_cache = json.load(f)
    except Exception as e:
        logger.error(f"Failed to load StreetScope data: {e}")
        
    return _streetscope_cache

def get_pedestrian_factor(lat, lng, radius_meters=500):
    """
    Returns a pedestrian safety/activity factor from StreetScope.
    If no data is available for this coordinate, returns None.
    """
    data = _load_streetscope_data()
    if not data:
        return None # Unavailable
        
    nearest = None
    min_dist = float('inf')
    
    for point in data:
        dist = _haversine(lat, lng, point['lat'], point['lng'])
        if dist < min_dist:
            min_dist = dist
            nearest = point
            
    if nearest and min_dist <= radius_meters:
        return nearest.get('pedestrian_score', 0)
        
    return None
