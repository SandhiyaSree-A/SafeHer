package com.safeher.app.data.model

data class Journey(
    val journeyId: String = "",
    val userId: String = "",
    val originLat: Double = 0.0,
    val originLng: Double = 0.0,
    val destinationAddress: String = "",
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val selectedRouteId: String = "",
    val selectedRouteName: String = "",
    val routeScore: Double = 0.0,
    val riskLabel: String = "",
    val distance: String = "",
    val duration: String = "",
    val disclaimer: String = "Risk-awareness estimate for prototype/demo purposes only, not a guarantee of real-world safety or crime prediction",
    val polylinePoints: List<RoutePoint> = emptyList(),
    val status: String = "planned", // planned, active, completed
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val lastPingAt: Long = 0L,
    val isDeviated: Boolean = false,
    val deviationAlertActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
