package com.safeher.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserPreferencesRepository(
    private val context: Context? = null
) {
    private val appContext: Context? by lazy {
        context ?: try {
            FirebaseApp.getInstance().applicationContext
        } catch (_: Exception) {
            null
        }
    }

    private val prefs: SharedPreferences? by lazy {
        appContext?.getSharedPreferences("safeher_user_preferences", Context.MODE_PRIVATE)
    }

    private val firestore = FirebaseFirestore.getInstance()

    fun getHomeAddress(userId: String): String? {
        val key = if (userId.isNotBlank()) "home_address_$userId" else "home_address_default"
        return prefs?.getString(key, null)?.takeIf { it.isNotBlank() }
    }

    fun hasHomeAddress(userId: String): Boolean {
        return !getHomeAddress(userId).isNullOrBlank()
    }

    suspend fun saveHomeAddress(userId: String, address: String) = withContext(Dispatchers.IO) {
        val trimmed = address.trim()
        if (trimmed.isBlank()) return@withContext

        // 1. Save locally for instant offline access
        val key = if (userId.isNotBlank()) "home_address_$userId" else "home_address_default"
        prefs?.edit()?.putString(key, trimmed)?.apply()

        // 2. Sync to Firestore user document asynchronously
        if (userId.isNotBlank()) {
            try {
                firestore.collection("users").document(userId)
                    .update("homeAddress", trimmed)
            } catch (_: Exception) {
                // Best effort cloud sync, offline preferences persist regardless
            }
        }
    }

    suspend fun clearHomeAddress(userId: String) = withContext(Dispatchers.IO) {
        val key = if (userId.isNotBlank()) "home_address_$userId" else "home_address_default"
        prefs?.edit()?.remove(key)?.apply()
    }
}
