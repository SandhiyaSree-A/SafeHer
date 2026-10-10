package com.safeher.app.data.model

data class SafestRoute(
    val id: String = "",
    val journeyId: String = "",
    val userId: String = "",
    val routeId: String = "",
    val routeName: String = "",
    val originLat: Double = 0.0,
    val originLng: Double = 0.0,
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val destinationAddress: String = "",
    val transportMode: String = "driving",
    val compositeScore: Double = 0.0,
    val riskLabel: String = "",
    val distance: String = "",
    val duration: String = "",
    val markedSafeAt: Long = System.currentTimeMillis()
)
