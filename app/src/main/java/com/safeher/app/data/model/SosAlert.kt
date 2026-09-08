package com.safeher.app.data.model

data class SosAlert(
    val alertId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = STATUS_ACTIVE,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: Long? = null,
    val resolvedAt: Long? = null
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_ACKNOWLEDGED = "acknowledged"
        const val STATUS_RESOLVED = "resolved"
    }
}
