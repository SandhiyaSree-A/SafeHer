from app.providers.osm_activity_provider import get_activity_density

def calculate_activity_density_score(lat, lng):
    """
    Calculates a normalized 0-100 activity density score.
    Returns (score, reason_if_unavailable).
    """
    count = get_activity_density(lat, lng)
    if count is None:
        return None, "Overpass OSM data unavailable"
    
    # Normalize: Assuming 20 POIs in the radius is considered 100% "active"
    score = min(100, (count / 20.0) * 100)
    return score, None
