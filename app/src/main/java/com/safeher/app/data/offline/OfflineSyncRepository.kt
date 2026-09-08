package com.safeher.app.data.offline

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class OfflineSyncRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database = SafeHerDatabase.get(appContext)
    private val connectivity = ConnectivityRepository.get(appContext)
    private val firestore = FirebaseFirestore.getInstance()

    fun currentlyOnline(): Boolean = connectivity.currentlyOnline()

    suspend fun queue(type: String, payload: JSONObject) = withContext(Dispatchers.IO) {
        // Simulated offline mode: local queuing plus SMS fallback, not device-to-device mesh networking.
        database.pendingSyncDao().insert(PendingSync(type = type, payload = payload.toString()))
    }

    suspend fun writeLocationPing(uid: String, journeyId: String?, lat: Double, lng: Double, timestamp: Long) {
        val payload = JSONObject().put("uid", uid).put("journeyId", journeyId ?: "").put("lat", lat).put("lng", lng).put("timestamp", timestamp)
        if (!connectivity.currentlyOnline()) return queue("location_ping", payload)
        try {
            val userReference = firestore.collection("users").document(uid)
            val location = mapOf("lat" to lat, "lng" to lng, "updatedAt" to timestamp)
            try {
                userReference.update("location", location).await()
            } catch (_: Exception) {
                userReference.set(mapOf("location" to location), com.google.firebase.firestore.SetOptions.merge()).await()
            }
            if (!journeyId.isNullOrBlank()) {
                firestore.collection("journeys").document(journeyId).update(mapOf("currentLat" to lat, "currentLng" to lng, "lastPingAt" to timestamp)).await()
                firestore.collection("journeys").document(journeyId).collection("pings").add(mapOf("lat" to lat, "lng" to lng, "timestamp" to timestamp)).await()
            }
        } catch (error: Exception) {
            if (!connectivity.currentlyOnline()) queue("location_ping", payload) else throw error
        }
    }

    suspend fun writeJourneyUpdate(journeyId: String, updates: Map<String, Any>, type: String = "journey_update") {
        val json = JSONObject().put("journeyId", journeyId).put("updates", JSONObject(updates))
        if (!connectivity.currentlyOnline()) return queue(type, json)
        try {
            firestore.collection("journeys").document(journeyId).update(updates).await()
        } catch (error: Exception) {
            if (!connectivity.currentlyOnline()) queue(type, json) else throw error
        }
    }

    suspend fun writeJourneyDocument(journeyId: String, data: Map<String, Any?>) {
        val json = JSONObject().put("journeyId", journeyId).put("operation", "set").put("data", JSONObject(data))
        if (!connectivity.currentlyOnline()) return queue("journey_update", json)
        try {
            firestore.collection("journeys").document(journeyId).set(data).await()
        } catch (error: Exception) {
            if (!connectivity.currentlyOnline()) queue("journey_update", json) else throw error
        }
    }

    suspend fun writeSosAlert(alertId: String, alert: Map<String, Any?>): Boolean {
        val json = JSONObject().put("alertId", alertId).put("alert", JSONObject(alert))
        try {
            firestore.collection("sos_alerts").document(alertId).set(alert).await()
            return true
        } catch (error: Exception) {
            if (!connectivity.currentlyOnline()) {
                queue("sos_alert", json)
                return false
            }
            throw error
        }
    }

    suspend fun syncPending() = withContext(Dispatchers.IO) {
        val dao = database.pendingSyncDao()
        dao.getUnsynced().forEach { pending ->
            try {
                val payload = JSONObject(pending.payload)
                when (pending.type) {
                    "location_ping" -> writeLocationPing(payload.getString("uid"), payload.optString("journeyId").ifBlank { null }, payload.getDouble("lat"), payload.getDouble("lng"), payload.getLong("timestamp"))
                    "sos_alert" -> firestore.collection("sos_alerts").document(payload.getString("alertId")).set(payload.getJSONObject("alert").toMap()).await()
                    "deviation_flag", "journey_update" -> {
                        val journeyReference = firestore.collection("journeys").document(payload.getString("journeyId"))
                        if (payload.optString("operation") == "set") {
                            journeyReference.set(payload.getJSONObject("data").toMap()).await()
                        } else {
                            journeyReference.update(payload.getJSONObject("updates").toMap()).await()
                        }
                    }
                }
                dao.markSynced(pending.id)
            } catch (_: Exception) {
                return@withContext
            }
        }
    }

    companion object {
        @Volatile private var instance: OfflineSyncRepository? = null
        fun get(context: Context): OfflineSyncRepository = instance ?: synchronized(this) {
            instance ?: OfflineSyncRepository(context).also { instance = it }
        }
    }
}

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = get(key)) {
        is JSONObject -> value.toMap()
        JSONObject.NULL -> null
        else -> value
    }
}