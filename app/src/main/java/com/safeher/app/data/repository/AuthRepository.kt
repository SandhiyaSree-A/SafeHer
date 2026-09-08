package com.safeher.app.data.repository

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.safeher.app.data.model.User
import java.util.concurrent.TimeUnit

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun getCurrentUser() = auth.currentUser

    fun sendOtp(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun signInWithCredential(
        credential: PhoneAuthCredential,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    syncUserDocument(
                        uid = firebaseUser.uid,
                        phone = firebaseUser.phoneNumber ?: "",
                        onSuccess = onSuccess,
                        onFailure = onFailure
                    )
                } else {
                    onFailure(Exception("Firebase user is null after sign in."))
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun syncUserDocument(
        uid: String,
        phone: String,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userDocRef = firestore.collection("users").document(uid)
        userDocRef.get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val name = documentSnapshot.getString("name") ?: "User"
                    val userPhone = documentSnapshot.getString("phone") ?: phone
                    val role = documentSnapshot.getString("role") ?: User.ROLE_USER
                    val createdAt = documentSnapshot.getLong("createdAt") ?: System.currentTimeMillis()
                    val user = User(
                        uid = uid,
                        name = name,
                        phone = userPhone,
                        role = role,
                        createdAt = createdAt
                    )
                    onSuccess(user)
                } else {
                    val newUser = User(
                        uid = uid,
                        name = "User",
                        phone = phone,
                        role = User.ROLE_USER,
                        createdAt = System.currentTimeMillis()
                    )
                    val userData = mapOf(
                        "name" to newUser.name,
                        "phone" to newUser.phone,
                        "role" to newUser.role,
                        "createdAt" to newUser.createdAt
                    )
                    userDocRef.set(userData)
                        .addOnSuccessListener {
                            onSuccess(newUser)
                        }
                        .addOnFailureListener(onFailure)
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun signOut() {
        auth.signOut()
    }
}
