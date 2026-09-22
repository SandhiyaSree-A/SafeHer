from app.providers.itd_provider import get_traffic_factor

def calculate_traffic_score(lat, lng):
    """
    Calculates a normalized 0-100 traffic score.
    Returns (score, reason_if_unavailable).
    """
    volume = get_traffic_factor(lat, lng)
    if volume is None:
        return None, "ITD traffic data unavailable"
    
    # Normalize: Assuming volume 500 is max traffic
    score = min(100, (volume / 500.0) * 100)
    return score, None
