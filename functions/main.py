"""
main.py
Firebase Cloud Function: scoreRoute
Backend for SafeHer Route Risk Scoring Engine.

Loads pretrained model from Hugging Face:
avnisinghal001/narisafe-risk-awareness-model (best_no_location_threshold_model.joblib)

ETHICAL DISCLAIMER:
"Risk-awareness estimate for prototype/demo purposes only, not a guarantee of real-world safety or crime prediction"
This disclaimer MUST be preserved in API responses and rendered in the Android client UI.
"""

import os
import joblib
import urllib.request
import pandas as pd
from flask import Flask, request, jsonify
from feature_calculator import build_feature_dataframe

# Mandatory Ethical Disclaimer per Hugging Face model guidelines
MANDATORY_DISCLAIMER = "Risk-awareness estimate for prototype/demo purposes only, not a guarantee of real-world safety or crime prediction"

HF_MODEL_URL = "https://huggingface.co/avnisinghal001/narisafe-risk-awareness-model/resolve/main/best_no_location_threshold_model.joblib"
MODEL_LOCAL_PATH = os.path.join(os.path.dirname(__file__), "best_model.joblib")

# Model lazy loading singleton
_model_dict = None

def load_model():
    global _model_dict
    if _model_dict is None:
        if not os.path.exists(MODEL_LOCAL_PATH):
            print(f"Downloading Hugging Face model from {HF_MODEL_URL}...")
            urllib.request.urlretrieve(HF_MODEL_URL, MODEL_LOCAL_PATH)
            print("Model downloaded successfully.")
        print(f"Loading joblib model from {MODEL_LOCAL_PATH}...")
        _model_dict = joblib.load(MODEL_LOCAL_PATH)
        print("Joblib model loaded successfully.")
    return _model_dict

def score_route_segments(origin_lat, origin_lng, dest_lat, dest_lng):
    """
    Computes 2-3 alternate routes between origin and destination,
    runs the Hugging Face risk model on engineered features per route,
    combines with mock lighting & crowd density, and returns ranked routes.
    """
    model_dict = load_model()
    pipeline = model_dict['pipeline']

    # Generate 3 realistic route polyline variations between origin and destination
    # Route 1: Direct Main Road / Arterial
    # Route 2: Eastern Bypass / Avenue
    # Route 3: Western Secondary / Residential Street

    d_lat = dest_lat - origin_lat
    d_lng = dest_lng - origin_lng

    routes_raw = [
        {
            "id": "route_1",
            "name": "Via Main Highway (Well Lit)",
            "distance": "5.4 km",
            "duration": "14 mins",
            "mock_lighting": 0.85,
            "mock_crowd": "high",
            "mock_crowd_num": 0.90,
            "path": [
                [origin_lat, origin_lng],
                [origin_lat + d_lat * 0.3, origin_lng + d_lng * 0.25],
                [origin_lat + d_lat * 0.7, origin_lng + d_lng * 0.75],
                [dest_lat, dest_lng]
            ]
        },
        {
            "id": "route_2",
            "name": "Via Central Avenue",
            "distance": "6.1 km",
            "duration": "17 mins",
            "mock_lighting": 0.65,
            "mock_crowd": "medium",
            "mock_crowd_num": 0.50,
            "path": [
                [origin_lat, origin_lng],
                [origin_lat + d_lat * 0.2, origin_lng + d_lng * 0.45],
                [origin_lat + d_lat * 0.8, origin_lng + d_lng * 0.55],
                [dest_lat, dest_lng]
            ]
        },
        {
            "id": "route_3",
            "name": "Via Ring Road (Secondary Street)",
            "distance": "7.2 km",
            "duration": "21 mins",
            "mock_lighting": 0.40,
            "mock_crowd": "low",
            "mock_crowd_num": 0.25,
            "path": [
                [origin_lat, origin_lng],
                [origin_lat + d_lat * 0.4, origin_lng - d_lng * 0.15],
                [origin_lat + d_lat * 0.85, origin_lng + d_lng * 0.35],
                [dest_lat, dest_lng]
            ]
        }
    ]

    scored_routes = []
    for r in routes_raw:
        # Build pandas dataframe with real OSM features + segment mock lighting/crowd
        df_features = build_feature_dataframe(r["path"], mock_lighting=r["mock_lighting"], mock_crowd=r["mock_crowd"])
        
        # Hugging Face Model Prediction (outputs 'low', 'medium', or 'high' risk awareness label)
        pred_label = pipeline.predict(df_features)[0].lower() # 'low', 'medium', 'high'

        # Map model risk label to numeric safety score (Low risk = 1.0, Medium = 0.5, High = 0.0)
        if pred_label == 'low':
            model_risk_score = 1.0
        elif pred_label == 'medium':
            model_risk_score = 0.5
        else:
            model_risk_score = 0.0

        # Composite Score Calculation (Requirement 4):
        # score = w1*model_risk_score + w2*mock_lighting + w3*mock_crowd_density
        w1, w2, w3 = 0.50, 0.30, 0.20
        composite_score = round(w1 * model_risk_score + w2 * r["mock_lighting"] + w3 * r["mock_crowd_num"], 2)

        # Risk label for display
        if composite_score >= 0.70:
            display_risk = "Low Risk (Safest)"
        elif composite_score >= 0.45:
            display_risk = "Medium Risk"
        else:
            display_risk = "High Risk"

        scored_routes.append({
            "routeId": r["id"],
            "name": r["name"],
            "distance": r["distance"],
            "duration": r["duration"],
            "compositeScore": composite_score,
            "modelRiskLabel": pred_label,
            "modelRiskScore": model_risk_score,
            "displayRisk": display_risk,
            "lightingScore": r["mock_lighting"],
            "crowdDensity": r["mock_crowd"],
            "disclaimer": MANDATORY_DISCLAIMER,
            "points": [{"lat": p[0], "lng": p[1]} for p in r["path"]]
        })

    # Sort routes highest score first (Requirement 5)
    scored_routes.sort(key=lambda x: x["compositeScore"], reverse=True)
    return scored_routes

def score_custom_routes(routes_input):
    """
    Scores dynamic route objects provided by the client with actual decoded polylines.
    """
    model_dict = load_model()
    pipeline = model_dict['pipeline']

    scored_routes = []
    for idx, r in enumerate(routes_input):
        route_id = r.get("routeId", f"route_{idx + 1}")
        name = r.get("name", f"Route {idx + 1}")
        distance = r.get("distance", "N/A")
        duration = r.get("duration", "N/A")
        
        raw_pts = r.get("points", [])
        path = []
        for p in raw_pts:
            if isinstance(p, dict):
                path.append([float(p.get("lat", 0.0)), float(p.get("lng", 0.0))])
            elif isinstance(p, (list, tuple)) and len(p) >= 2:
                path.append([float(p[0]), float(p[1])])

        if not path:
            continue

        mock_lighting = float(r.get("mock_lighting", 0.70))
        mock_crowd = str(r.get("mock_crowd", "medium"))
        crowd_num_map = {"high": 0.90, "medium": 0.50, "low": 0.25}
        mock_crowd_num = float(r.get("mock_crowd_num", crowd_num_map.get(mock_crowd.lower(), 0.50)))

        df_features = build_feature_dataframe(path, mock_lighting=mock_lighting, mock_crowd=mock_crowd)
        pred_label = pipeline.predict(df_features)[0].lower()

        if pred_label == 'low':
            model_risk_score = 1.0
        elif pred_label == 'medium':
            model_risk_score = 0.5
        else:
            model_risk_score = 0.0

        w1, w2, w3 = 0.50, 0.30, 0.20
        composite_score = round(w1 * model_risk_score + w2 * mock_lighting + w3 * mock_crowd_num, 2)

        if composite_score >= 0.70:
            display_risk = "Low Risk (Safest)"
        elif composite_score >= 0.45:
            display_risk = "Medium Risk"
        else:
            display_risk = "High Risk"

        scored_routes.append({
            "routeId": route_id,
            "name": name,
            "distance": distance,
            "duration": duration,
            "compositeScore": composite_score,
            "modelRiskLabel": pred_label,
            "modelRiskScore": model_risk_score,
            "displayRisk": display_risk,
            "lightingScore": mock_lighting,
            "crowdDensity": mock_crowd,
            "disclaimer": MANDATORY_DISCLAIMER,
            "points": [{"lat": p[0], "lng": p[1]} for p in path]
        })

    scored_routes.sort(key=lambda x: x["compositeScore"], reverse=True)
    return scored_routes

# Flask app runner for local dev and Cloud Function entrypoint
app = Flask(__name__)

@app.route('/scoreRoute', methods=['POST', 'GET'])
def score_route_http():
    if request.method == 'OPTIONS':
        return jsonify({}), 200

    req_json = request.get_json(silent=True) or request.args
    req_routes = req_json.get('routes') if isinstance(req_json, dict) else None

    try:
        if req_routes and isinstance(req_routes, list) and len(req_routes) > 0:
            routes = score_custom_routes(req_routes)
        else:
            origin_lat = float(req_json.get('originLat', 28.6139))
            origin_lng = float(req_json.get('originLng', 77.2090))
            dest_lat = float(req_json.get('destLat', 28.5355))
            dest_lng = float(req_json.get('destLng', 77.3910))
            routes = score_route_segments(origin_lat, origin_lng, dest_lat, dest_lng)

        return jsonify({
            "status": "success",
            "disclaimer": MANDATORY_DISCLAIMER,
            "routes": routes
        })
    except Exception as e:
        return jsonify({
            "status": "error",
            "message": str(e),
            "disclaimer": MANDATORY_DISCLAIMER
        }), 500

if __name__ == '__main__':
    print("Starting SafeHer scoreRoute Backend Server on port 5000...")
    load_model()
    app.run(host='0.0.0.0', port=5000, debug=True)
