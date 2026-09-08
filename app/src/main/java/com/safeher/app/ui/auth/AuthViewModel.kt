package com.safeher.app.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.safeher.app.data.model.User
import com.safeher.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthUiState {
    object Idle : AuthUiState()
    object CodeSent : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _verificationId = MutableStateFlow("")
    val verificationId: StateFlow<String> = _verificationId.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _otpCode = MutableStateFlow("")
    val otpCode: StateFlow<String> = _otpCode.asStateFlow()

    fun updatePhoneNumber(number: String) {
        _phoneNumber.value = number
    }

    fun updateOtpCode(code: String) {
        _otpCode.value = code
    }

    fun sendOtp(activity: Activity) {
        val phone = _phoneNumber.value.trim()
        if (phone.isEmpty()) {
            _uiState.value = AuthUiState.Error("Please enter a valid phone number.")
            return
        }

        _uiState.value = AuthUiState.Loading

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "Verification failed.")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                _verificationId.value = verificationId
                _uiState.value = AuthUiState.CodeSent
            }
        }

        repository.sendOtp(phone, activity, callbacks)
    }

    fun verifyOtp() {
        val verId = _verificationId.value
        val code = _otpCode.value.trim()

        if (verId.isEmpty() || code.length < 6) {
            _uiState.value = AuthUiState.Error("Please enter the 6-digit OTP code.")
            return
        }

        _uiState.value = AuthUiState.Loading
        val credential = PhoneAuthProvider.getCredential(verId, code)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        repository.signInWithCredential(
            credential = credential,
            onSuccess = { user ->
                _uiState.value = AuthUiState.Success(user)
            },
            onFailure = { ex ->
                _uiState.value = AuthUiState.Error(ex.localizedMessage ?: "Sign-in failed.")
            }
        )
    }

    fun checkExistingSession() {
        val currentUser = repository.getCurrentUser()
        if (currentUser != null) {
            _uiState.value = AuthUiState.Loading
            repository.syncUserDocument(
                uid = currentUser.uid,
                phone = currentUser.phoneNumber ?: "",
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = { ex ->
                    _uiState.value = AuthUiState.Error(ex.localizedMessage ?: "Session sync failed.")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
