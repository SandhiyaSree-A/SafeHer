package com.safeher.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.safeher.app.data.model.SosAlert
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AdminRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun getActiveSosAlertsFlow(): Flow<List<SosAlert>> = callbackFlow {
        val query = firestore.collection("sos_alerts")
            .whereIn("status", listOf(SosAlert.STATUS_ACTIVE, SosAlert.STATUS_ACKNOWLEDGED))
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            val alerts = snapshot?.documents?.mapNotNull { doc ->
                val locationMap = doc.get("location") as? Map<*, *>
                val lat = (locationMap?.get("lat") as? Number)?.toDouble() ?: 0.0
                val lng = (locationMap?.get("lng") as? Number)?.toDouble() ?: 0.0
                
                SosAlert(
                    alertId = doc.id,
                    userId = doc.getString("userId") ?: "",
                    userName = doc.getString("userName") ?: "Unknown User",
                    userPhone = doc.getString("userPhone") ?: "",
                    lat = lat,
                    lng = lng,
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                    status = doc.getString("status") ?: SosAlert.STATUS_ACTIVE,
                    acknowledgedBy = doc.getString("acknowledgedBy"),
                    acknowledgedAt = doc.getLong("acknowledgedAt"),
                    resolvedAt = doc.getLong("resolvedAt")
                )
            } ?: emptyList()

            trySend(alerts)
        }

        awaitClose { listener.remove() }
    }

    fun acknowledgeAlert(
        alertId: String,
        adminUid: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (alertId.isBlank()) return
        val updates = mapOf(
            "status" to SosAlert.STATUS_ACKNOWLEDGED,
            "acknowledgedBy" to adminUid,
            "acknowledgedAt" to System.currentTimeMillis()
        )
        firestore.collection("sos_alerts").document(alertId)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun resolveAlert(
        alertId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (alertId.isBlank()) return
        val updates = mapOf(
            "status" to SosAlert.STATUS_RESOLVED,
            "resolvedAt" to System.currentTimeMillis()
        )
        firestore.collection("sos_alerts").document(alertId)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}
