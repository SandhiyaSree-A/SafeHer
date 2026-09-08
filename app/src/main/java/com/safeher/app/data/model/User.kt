package com.safeher.app.data.model

data class User(
    val uid: String = "",
    val name: String = "User",
    val phone: String = "",
    val role: String = ROLE_USER,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ADMIN = "security_room_admin"
    }
}
