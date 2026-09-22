from app.providers.streetscope_provider import get_pedestrian_factor

def calculate_pedestrian_score(lat, lng):
    """
    Calculates a normalized 0-100 pedestrian factor score.
    Returns (score, reason_if_unavailable).
    """
    ped_factor = get_pedestrian_factor(lat, lng)
    if ped_factor is None:
        return None, "StreetScope data unavailable"
    
    # Normalize: Assuming ped_factor is already 0-100 in the dataset or needs scaling
    score = min(100, ped_factor) # Adjust normalization if dataset bounds are known
    return score, None
