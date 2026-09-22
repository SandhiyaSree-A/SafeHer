import requests
import logging
from app.config import OVERPASS_BASE_URL

logger = logging.getLogger(__name__)

def get_activity_density(lat, lng, radius_meters=500):
    """
    Fetches the number of POIs (amenities, shops, etc.) around a coordinate using Overpass API.
    Returns the count of relevant POIs, or None if unavailable.
    """
    try:
        query = f"""
        [out:json][timeout:5];
        (
          node["amenity"](around:{radius_meters},{lat},{lng});
          node["shop"](around:{radius_meters},{lat},{lng});
          node["tourism"](around:{radius_meters},{lat},{lng});
          node["leisure"](around:{radius_meters},{lat},{lng});
        );
        out count;
        """
        
        response = requests.post(OVERPASS_BASE_URL, data={'data': query}, timeout=6)
        
        if response.status_code == 200:
            data = response.json()
            if 'elements' in data and len(data['elements']) > 0:
                count = data['elements'][0].get('tags', {}).get('nodes', 0)
                # Overpass `out count` returns nodes, ways, rels counts in tags
                return int(count)
            return 0
        
        logger.warning(f"Overpass API returned {response.status_code} for {lat}, {lng}")
        return None
    except Exception as e:
        logger.error(f"Error fetching OSM Activity data: {e}")
        return None
