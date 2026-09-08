package com.safeher.app.data.model

import com.google.android.gms.maps.model.LatLng

data class RoutePoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0
) {
    fun toLatLng(): LatLng = LatLng(lat, lng)
}

data class RouteOption(
    val routeId: String = "",
    val name: String = "",
    val distance: String = "",
    val duration: String = "",
    val compositeScore: Double = 0.0,
    val modelRiskLabel: String = "",
    val displayRisk: String = "",
    val lightingScore: Double = 0.0,
    val crowdDensity: String = "",
    val disclaimer: String = "Risk-awareness estimate for prototype/demo purposes only, not a guarantee of real-world safety or crime prediction",
    val points: List<RoutePoint> = emptyList()
)
