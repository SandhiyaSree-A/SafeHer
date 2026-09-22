import requests
import logging
from app.config import WORLDPOP_BASE_URL, WORLDPOP_API_KEY

logger = logging.getLogger(__name__)

def get_population_stats(lat, lng, radius_meters=500):
    """
    Fetches population statistics around a specific coordinate using WorldPop.
    Returns the total estimated population within the given radius, or None if unavailable.
    """
    try:
        # Construct the WorldPop API request for a point buffer
        # This is an approximation based on WorldPop documentation.
        params = {
            'dataset': 'wpgp',
            'year': 2020, # Use latest available stable year
            'lat': lat,
            'lon': lng,
            'radius': radius_meters,
            'run': 'get_population'
        }
        
        headers = {}
        if WORLDPOP_API_KEY:
            headers['Authorization'] = f'Bearer {WORLDPOP_API_KEY}'
            
        # Optional: Some WorldPop endpoints might differ, 
        # but we'll implement a robust try-except to handle failures
        # Note: If no real-time capability is intended by user for large scale, 
        # this could also check local rasters.
        response = requests.get(WORLDPOP_BASE_URL, params=params, headers=headers, timeout=5)
        
        if response.status_code == 200:
            data = response.json()
            # Extract population, this key depends on actual API response structure
            if 'data' in data and 'total_population' in data['data']:
                return data['data']['total_population']
        
        logger.warning(f"WorldPop API returned {response.status_code} for {lat}, {lng}")
        return None
    except Exception as e:
        logger.error(f"Error fetching WorldPop data: {e}")
        return None
