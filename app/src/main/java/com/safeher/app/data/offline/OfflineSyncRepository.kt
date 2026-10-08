package com.safeher.app.data.offline

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OfflineSyncRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database = SafeHerDatabase.get(appContext)
    private val connectivity = ConnectivityRepository.get(appContext)
    private val firestore = FirebaseFirestore.getInstance()



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

    private suspend fun postSosEventPg(alertId: String, alert: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val endpoint = "http://10.0.2.2:8000/sos-events"
        val location = alert["location"] as? Map<*, *>
        val lat = (location?.get("lat") as? Number)?.toDouble() ?: 0.0
        val lng = (location?.get("lng") as? Number)?.toDouble() ?: 0.0
        val userId = alert["userId"] as? String ?: ""
        val timestamp = (alert["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

        val json = JSONObject().apply {
            put("alertId", alertId)
            put("userId", userId)
            put("lat", lat)
            put("lng", lng)
            put("timestamp", timestamp)
        }

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.bufferedWriter().use { it.write(json.toString()) }
        if (conn.responseCode !in 200..299) {
            throw Exception("Failed to post sos event: ${conn.responseCode}")
        }
    }

    suspend fun writeSosAlert(alertId: String, alert: Map<String, Any?>): Boolean {
        val json = JSONObject().put("alertId", alertId).put("alert", JSONObject(alert))
        
        // Try the PG backend call in the background. Don't let it fail the main Firestore alert.
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            if (!connectivity.currentlyOnline()) {
                queue("sos_event_pg", json)
            } else {
                try {
                    postSosEventPg(alertId, alert)
                } catch (_: Exception) {
                    queue("sos_event_pg", json)
                }
            }
        }

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
                    "sos_event_pg" -> postSosEventPg(payload.getString("alertId"), payload.getJSONObject("alert").toMap())
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