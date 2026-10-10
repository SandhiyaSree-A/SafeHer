package com.safeher.app.data.model

data class RouteFeedback(
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
    val wasSafe: Boolean = false,
    val reasons: List<String> = emptyList(),
    val customReason: String = "",
    val submittedAt: Long = System.currentTimeMillis()
)
