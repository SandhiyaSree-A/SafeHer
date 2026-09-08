package com.safeher.app.data.model

data class LocationData(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)
