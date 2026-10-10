package com.safeher.app.data.model

import com.google.android.gms.maps.model.LatLng

data class RoutePoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0
) {
    fun toLatLng(): LatLng = LatLng(lat, lng)
}

data class RouteTurnStep(
    val instruction: String = "",
    val roadName: String = "",
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0
)

data class SafetiPinMetrics(
    val lightingRating: Double = 0.85,          // 0.0 to 1.0 (Night light visibility)
    val eyesOnStreetRating: Double = 0.80,      // 0.0 to 1.0 (Presence of shops, windows, activity)
    val crowdDensityRating: Double = 0.90,      // 0.0 to 1.0 (Pedestrian flow & safety in numbers)
    val patrolProximityRating: Double = 0.75,    // 0.0 to 1.0 (Police station / security booth proximity)
    val transitAccessRating: Double = 0.82,     // 0.0 to 1.0 (Metro station / bus stop access)
    val overallSafetyAuditScore: Double = 0.84
)

/**
 * Historical averages from the route_trust_scores table for this corridor.
 * Null when no prior data exists (first time the route is computed).
 */
data class HistoricalTrustData(
    val historyCount: Int = 0,               // Number of past computations
    val avgCompositeScore: Double = 0.0,     // Average overall safety score
    val avgLightingSafety: Double = 0.0,     // Average lighting safety factor
    val avgTraffic: Double = 0.0,            // Average traffic factor
    val avgHumanPresence: Double = 0.0,      // Average human-presence factor
    val avgPedestrian: Double = 0.0,         // Average pedestrian factor
    val avgSosHotspot: Double? = null,       // Average SOS hotspot factor (null = never available)
    val avgConfidence: Double = 1.0          // Average scoring confidence
)

data class RouteOption(
    val routeId: String = "",
    val name: String = "",
    val viaRoute: String = "",
    val majorAreasCovered: String = "",
    val transportMode: String = "driving",       // "driving", "walking", "bicycling"
    val distance: String = "",
    val duration: String = "",
    val compositeScore: Double = 0.0,
    val modelRiskLabel: String = "",
    val displayRisk: String = "",
    val lightingScore: Double = 0.0,
    val crowdDensity: String = "",
    val trafficCondition: String = "Smooth",
    val trafficScore: Double = 0.85,
    val humanPresenceScore: Double = 0.0,
    val activityDensityScore: Double = 0.0,
    val pedestrianScore: Double = 0.0,
    val confidenceScore: Double = 1.0,
    // SOS Hotspot factor — null means "not available" (OSRM / local fallback paths)
    val sosHotspotScore: Double? = null,
    val sosHotspotCount: Int = 0,
    val sosHotspots: List<RoutePoint> = emptyList(),
    val darkSpots: List<RoutePoint> = emptyList(),
    val turnSteps: List<RouteTurnStep> = emptyList(),
    val safetiPinMetrics: SafetiPinMetrics = SafetiPinMetrics(),
    val disclaimer: String = "Risk-awareness estimate for prototype/demo purposes only, not a guarantee of real-world safety or crime prediction",
    val points: List<RoutePoint> = emptyList(),
    /** Historical DB-averaged trust scores for this corridor; null on first computation. */
    val historicalTrust: HistoricalTrustData? = null
)
