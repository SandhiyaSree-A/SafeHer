package com.safeher.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.safeher.app.data.model.EmergencyContact
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ContactRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun getEmergencyContacts(uid: String): Flow<List<EmergencyContact>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val collectionRef = firestore
            .collection("users")
            .document(uid)
            .collection("emergency_contacts")

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val contacts = snapshot?.documents?.mapNotNull { doc ->
                val id = doc.id
                val name = doc.getString("name") ?: ""
                val phone = doc.getString("phone") ?: ""
                val relation = doc.getString("relation") ?: ""
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                EmergencyContact(
                    id = id,
                    name = name,
                    phone = phone,
                    relation = relation,
                    createdAt = createdAt
                )
            } ?: emptyList()

            trySend(contacts)
        }

        awaitClose { listener.remove() }
    }

    fun addEmergencyContact(
        uid: String,
        name: String,
        phone: String,
        relation: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (uid.isBlank()) {
            onFailure(Exception("User UID is empty"))
            return
        }

        val docRef = firestore
            .collection("users")
            .document(uid)
            .collection("emergency_contacts")
            .document()

        val contactData = mapOf(
            "id" to docRef.id,
            "name" to name,
            "phone" to phone,
            "relation" to relation,
            "createdAt" to System.currentTimeMillis()
        )

        docRef.set(contactData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun deleteEmergencyContact(
        uid: String,
        contactId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (uid.isBlank() || contactId.isBlank()) return

        firestore
            .collection("users")
            .document(uid)
            .collection("emergency_contacts")
            .document(contactId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}
