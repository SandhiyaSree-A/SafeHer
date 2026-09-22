from app.providers.worldpop_provider import get_population_stats

def calculate_human_presence_score(lat, lng):
    """
    Calculates a normalized 0-100 human presence score.
    Returns (score, reason_if_unavailable).
    """
    pop = get_population_stats(lat, lng)
    if pop is None:
        return None, "WorldPop data unavailable"
    
    # Normalize: Assuming max population density we care about is 1000 people per 500m radius
    # You can adjust this threshold based on realistic bounds.
    score = min(100, (pop / 1000.0) * 100)
    return score, None
