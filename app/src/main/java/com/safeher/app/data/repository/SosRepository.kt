package com.safeher.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.safeher.app.data.offline.OfflineSyncRepository
import com.safeher.app.data.model.SosAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SosRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun createSosAlert(
        userId: String,
        userName: String,
        userPhone: String,
        lat: Double,
        lng: Double,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onQueued: () -> Unit = {}
    ) {
        val docRef = firestore.collection("sos_alerts").document()
        val alertId = docRef.id

        val alertData = mapOf(
            "alertId" to alertId,
            "userId" to userId,
            "userName" to userName,
            "userPhone" to userPhone,
            "location" to mapOf(
                "lat" to lat,
                "lng" to lng
            ),
            "timestamp" to System.currentTimeMillis(),
            "status" to SosAlert.STATUS_ACTIVE
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val writtenOnline = OfflineSyncRepository.get(docRef.firestore.app.applicationContext)
                    .writeSosAlert(alertId, alertData)
                if (writtenOnline) onSuccess(alertId) else onQueued()
            } catch (error: Exception) {
                onFailure(error)
            }
        }
    }

    fun resolveSosAlert(
        alertId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (alertId.isBlank()) return
        firestore.collection("sos_alerts").document(alertId)
            .update("status", SosAlert.STATUS_RESOLVED)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun getActiveSosAlertFlow(userId: String): Flow<SosAlert?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val query = firestore.collection("sos_alerts")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", SosAlert.STATUS_ACTIVE)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }

            val doc = snapshot?.documents?.firstOrNull()
            if (doc != null && doc.exists()) {
                val locationMap = doc.get("location") as? Map<*, *>
                val lat = (locationMap?.get("lat") as? Number)?.toDouble() ?: 0.0
                val lng = (locationMap?.get("lng") as? Number)?.toDouble() ?: 0.0
                val alert = SosAlert(
                    alertId = doc.id,
                    userId = doc.getString("userId") ?: "",
                    userName = doc.getString("userName") ?: "",
                    userPhone = doc.getString("userPhone") ?: "",
                    lat = lat,
                    lng = lng,
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                    status = doc.getString("status") ?: SosAlert.STATUS_ACTIVE
                )
                trySend(alert)
            } else {
                trySend(null)
            }
        }

        awaitClose { listener.remove() }
    }
}
