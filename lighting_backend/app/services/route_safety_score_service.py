import logging
from app.services.route_sampling_service import sample_route_by_distance
from app.services.route_lighting_service import analyze_route_lighting
from app.services.human_presence_service import calculate_human_presence_score
from app.services.activity_density_service import calculate_activity_density_score
from app.services.traffic_service import calculate_traffic_score
from app.services.pedestrian_service import calculate_pedestrian_score

logger = logging.getLogger(__name__)

# Default weights
DEFAULT_WEIGHTS = {
    'lighting': 0.30,
    'human_presence': 0.20,
    'activity_density': 0.20,
    'traffic': 0.15,
    'pedestrian': 0.15
}

def analyze_multi_factor_routes(routes, interval_meters=300):
    if not routes:
        raise ValueError("No routes available")

    route_results = []
    
    for route in routes:
        route_id = route["route_id"]
        sampled_points = sample_route_by_distance(route["coordinates"], interval_meters)
        
        # 1. Lighting (Always exists due to NASA data)
        lighting_result = analyze_route_lighting(sampled_points)
        lighting_score = lighting_result.get("average_light_score", 0)
        
        # We will calculate averages for the other factors across sampled points
        human_scores = []
        activity_scores = []
        traffic_scores = []
        pedestrian_scores = []
        
        reasons = {}
        
        for p in sampled_points:
            lat, lng = p["latitude"], p["longitude"]
            
            # Human Presence
            hs, hr = calculate_human_presence_score(lat, lng)
            if hs is not None: human_scores.append(hs)
            elif hr: reasons['human_presence'] = hr
            
            # Activity Density
            ads, adr = calculate_activity_density_score(lat, lng)
            if ads is not None: activity_scores.append(ads)
            elif adr: reasons['activity_density'] = adr
            
            # Traffic
            ts, tr = calculate_traffic_score(lat, lng)
            if ts is not None: traffic_scores.append(ts)
            elif tr: reasons['traffic'] = tr
                
            # Pedestrian
            ps, pr = calculate_pedestrian_score(lat, lng)
            if ps is not None: pedestrian_scores.append(ps)
            elif pr: reasons['pedestrian'] = pr
            
        avg_human = sum(human_scores)/len(human_scores) if human_scores else None
        avg_activity = sum(activity_scores)/len(activity_scores) if activity_scores else None
        avg_traffic = sum(traffic_scores)/len(traffic_scores) if traffic_scores else None
        avg_pedestrian = sum(pedestrian_scores)/len(pedestrian_scores) if pedestrian_scores else None
        
        # Normalize and calculate final score
        available_factors = {'lighting': lighting_score}
        if avg_human is not None: available_factors['human_presence'] = avg_human
        if avg_activity is not None: available_factors['activity_density'] = avg_activity
        if avg_traffic is not None: available_factors['traffic'] = avg_traffic
        if avg_pedestrian is not None: available_factors['pedestrian'] = avg_pedestrian
        
        total_weight = sum(DEFAULT_WEIGHTS[f] for f in available_factors.keys())
        final_score = 0
        for f, s in available_factors.items():
            normalized_weight = DEFAULT_WEIGHTS[f] / total_weight
            final_score += s * normalized_weight
            
        confidence = total_weight # Max is 1.0
        
        route_results.append({
            "route_id": route_id,
            "distance_meters": route["distance_meters"],
            "duration_seconds": route["duration_seconds"],
            "coordinates": route["coordinates"],
            "sampled_points": sampled_points,
            "lighting": lighting_result,
            "factors": {
                "lighting_score": lighting_score,
                "human_presence_score": avg_human,
                "activity_density_score": avg_activity,
                "traffic_score": avg_traffic,
                "pedestrian_score": avg_pedestrian,
                "final_safety_score": final_score,
                "confidence": confidence,
                "missing_reasons": reasons
            }
        })

    # Select best route
    safest_route = max(route_results, key=lambda r: r["factors"]["final_safety_score"])
    
    return {
        "total_routes": len(route_results),
        "safest_route_id": safest_route["route_id"],
        "safest_route": safest_route,
        "routes": route_results
    }
