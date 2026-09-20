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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.safeher.app.util.PhoneUtils

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        const val GENERIC_AUTH_ERROR = "Invalid name or password"
    }

    /** Verifies the OTP with Firebase and signs the user in with the phone credential. */
    fun signInWithPhoneCredential(
        credential: PhoneAuthCredential,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
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
    val firebaseUser = auth.currentUser
    if (firebaseUser == null) {
        onFailure(Exception("Phone not verified. Please verify the OTP again."))
        return
    }
    val normalizedPhone = PhoneUtils.normalizeIndian(phone)
    val emailCredential = EmailAuthProvider.getCredential(
        generateSyntheticEmail(normalizedPhone), password
    )

    firebaseUser.linkWithCredential(emailCredential)
        .addOnSuccessListener {
            val newUser = User(
                uid = firebaseUser.uid,
                name = name.trim(),
                phone = normalizedPhone,
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
        }
        .addOnFailureListener { ex ->
            auth.signOut()
            val alreadyExists = ex is FirebaseAuthUserCollisionException ||
                (ex as? FirebaseAuthException)?.errorCode == "ERROR_PROVIDER_ALREADY_LINKED"
            onFailure(
                if (alreadyExists) Exception("An account with this phone number already exists. Please sign in.")
                else ex
            )
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

        /**
     * Updates the current user's name/phone in Firestore and returns the
     * refreshed User object so the UI can update immediately.
     */
    fun updateProfile(
        uid: String,
        name: String,
        phone: String,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            onFailure(Exception("Name cannot be empty."))
            return
        }
        val normalizedPhone = PhoneUtils.normalizeIndian(phone.trim())
        if (!PhoneUtils.isValidPhone(normalizedPhone)) {
            onFailure(Exception("Enter a valid phone number (10-digit Indian mobile, or +country code)."))
            return
        }

        val userDocRef = firestore.collection("users").document(uid)
        userDocRef.get()
            .addOnSuccessListener { snapshot ->
                val role = snapshot.getString("role") ?: User.ROLE_USER
                val createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis()

                val updates = mapOf(
                    "name" to trimmedName,
                    "phone" to normalizedPhone
                )
                userDocRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        onSuccess(
                            User(
                                uid = uid,
                                name = trimmedName,
                                phone = normalizedPhone,
                                role = role,
                                createdAt = createdAt
                            )
                        )
                    }
                    .addOnFailureListener(onFailure)
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

    /** Derives a stable, unique email from a normalized phone number for Firebase Email auth. */
    private fun generateSyntheticEmail(normalizedPhone: String): String {
        // Strip the leading '+' and append a fixed domain so Firebase accepts it as an email.
        val digits = normalizedPhone.filter { it.isDigit() }
        return "user_$digits@safeher.app"
    }
}

