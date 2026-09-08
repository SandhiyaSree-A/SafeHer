package com.safeher.app.data.model

data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val relation: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
