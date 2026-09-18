package com.safeher.app.data.repository

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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

    companion object {
        const val GENERIC_AUTH_ERROR = "Invalid name or password"

        fun generateSyntheticEmail(phoneNumber: String): String {
            val cleanPhone = phoneNumber.filter { it.isLetterOrDigit() }.ifBlank { "user" }
            return "${cleanPhone.lowercase()}@safeher.app"
        }
    }

    fun getCurrentUser() = auth.currentUser

    /**
     * Sends OTP for signup verification.
     */
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

    /**
     * Creates account with synthetic email and chosen password after OTP verification.
     */
    fun createAccountWithPassword(
        name: String,
        phone: String,
        password: String,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val syntheticEmail = generateSyntheticEmail(phone)
        auth.createUserWithEmailAndPassword(syntheticEmail, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    val newUser = User(
                        uid = firebaseUser.uid,
                        name = name.trim(),
                        phone = phone.trim(),
                        role = User.ROLE_USER,
                        createdAt = System.currentTimeMillis()
                    )
                    val userData = mapOf(
                        "uid" to newUser.uid,
                        "name" to newUser.name,
                        "phone" to newUser.phone,
                        "role" to newUser.role,
                        "createdAt" to newUser.createdAt
                    )
                    firestore.collection("users").document(firebaseUser.uid)
                        .set(userData)
                        .addOnSuccessListener { onSuccess(newUser) }
                        .addOnFailureListener(onFailure)
                } else {
                    onFailure(Exception("Authentication user creation returned null."))
                }
            }
            .addOnFailureListener { ex ->
                if (ex is FirebaseAuthUserCollisionException) {
                    // If user already exists in auth, attempt signing in to update profile
                    auth.signInWithEmailAndPassword(syntheticEmail, password)
                        .addOnSuccessListener { authResult ->
                            val uid = authResult.user?.uid ?: ""
                            syncUserDocument(uid, phone, onSuccess, onFailure)
                        }
                        .addOnFailureListener {
                            onFailure(Exception("An account with this phone number already exists."))
                        }
                } else {
                    onFailure(ex)
                }
            }
    }

    /**
     * Looks up users in Firestore by Name.
     */
    fun lookupUsersByName(
        name: String,
        onSuccess: (List<User>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firestore.collection("users")
            .whereEqualTo("name", name.trim())
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    val userName = doc.getString("name") ?: name
                    val userPhone = doc.getString("phone") ?: ""
                    val role = doc.getString("role") ?: User.ROLE_USER
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    User(uid = uid, name = userName, phone = userPhone, role = role, createdAt = createdAt)
                }
                onSuccess(users)
            }
            .addOnFailureListener(onFailure)
    }

    /**
     * Performs password-based login by looking up the name in Firestore,
     * resolving disambiguation if multiple users share the same name,
     * and signing in via the synthetic email.
     */
    fun loginWithNameAndPassword(
        name: String,
        password: String,
        phoneDisambiguation: String? = null,
        onDisambiguationRequired: (List<User>) -> Unit,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || password.isEmpty()) {
            onFailure(Exception(GENERIC_AUTH_ERROR))
            return
        }

        lookupUsersByName(
            name = trimmedName,
            onSuccess = { matchingUsers ->
                when {
                    matchingUsers.isEmpty() -> {
                        onFailure(Exception(GENERIC_AUTH_ERROR))
                    }
                    matchingUsers.size == 1 -> {
                        val user = matchingUsers.first()
                        performSyntheticLogin(user, password, onSuccess, onFailure)
                    }
                    else -> {
                        // Multiple users share this name
                        if (phoneDisambiguation.isNullOrBlank()) {
                            onDisambiguationRequired(matchingUsers)
                        } else {
                            val cleanTarget = phoneDisambiguation.filter { it.isDigit() }
                            val matchedUser = matchingUsers.firstOrNull { u ->
                                val cleanUserPhone = u.phone.filter { it.isDigit() }
                                cleanUserPhone.endsWith(cleanTarget) || cleanTarget.endsWith(cleanUserPhone)
                            }
                            if (matchedUser != null) {
                                performSyntheticLogin(matchedUser, password, onSuccess, onFailure)
                            } else {
                                onFailure(Exception(GENERIC_AUTH_ERROR))
                            }
                        }
                    }
                }
            },
            onFailure = {
                onFailure(Exception(GENERIC_AUTH_ERROR))
            }
        )
    }

    private fun performSyntheticLogin(
        user: User,
        password: String,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val syntheticEmail = generateSyntheticEmail(user.phone)
        auth.signInWithEmailAndPassword(syntheticEmail, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: user.uid
                syncUserDocument(uid, user.phone, onSuccess, onFailure)
            }
            .addOnFailureListener {
                onFailure(Exception(GENERIC_AUTH_ERROR))
            }
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
                        "uid" to newUser.uid,
                        "name" to newUser.name,
                        "phone" to newUser.phone,
                        "role" to newUser.role,
                        "createdAt" to newUser.createdAt
                    )
                    userDocRef.set(userData)
                        .addOnSuccessListener { onSuccess(newUser) }
                        .addOnFailureListener(onFailure)
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun signOut() {
        auth.signOut()
    }
}

