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
import com.safeher.app.util.PhoneUtils

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object OtpSent : AuthUiState()
    object OtpVerified : AuthUiState()
    data class DisambiguationRequired(val message: String = "Multiple users share this name. Please enter your phone number to proceed.") : AuthUiState()
    data class Success(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isSignUpMode = MutableStateFlow(false)
    val isSignUpMode: StateFlow<Boolean> = _isSignUpMode.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _otpCode = MutableStateFlow("")
    val otpCode: StateFlow<String> = _otpCode.asStateFlow()

    private val _disambiguationPhone = MutableStateFlow("")
    val disambiguationPhone: StateFlow<String> = _disambiguationPhone.asStateFlow()

    private val _verificationId = MutableStateFlow("")
    val verificationId: StateFlow<String> = _verificationId.asStateFlow()

    private val _otpError = MutableStateFlow<String?>(null)
val otpError: StateFlow<String?> = _otpError.asStateFlow()

private val _isVerifyingOtp = MutableStateFlow(false)
val isVerifyingOtp: StateFlow<Boolean> = _isVerifyingOtp.asStateFlow()

    fun toggleAuthMode(signUp: Boolean) {
        _isSignUpMode.value = signUp
        _uiState.value = AuthUiState.Idle
        _otpCode.value = ""
        _password.value = ""
        _confirmPassword.value = ""
        _disambiguationPhone.value = ""
        _otpError.value = null
    }

    fun updateName(name: String) {
        _name.value = name
    }

    fun updatePhoneNumber(number: String) {
        _phoneNumber.value = number
    }

    fun updatePassword(pass: String) {
        _password.value = pass
    }

    fun updateConfirmPassword(pass: String) {
        _confirmPassword.value = pass
    }

    fun updateOtpCode(code: String) {
        _otpCode.value = code
    }

    fun updateDisambiguationPhone(phone: String) {
        _disambiguationPhone.value = phone
    }

    // SIGNUP FLOW: Step 1 -> Send OTP
    fun sendSignupOtp(activity: Activity) {
    val nameVal = _name.value.trim()
    val phoneVal = PhoneUtils.normalizeIndian(_phoneNumber.value.trim())

    if (nameVal.isEmpty()) {
        _uiState.value = AuthUiState.Error("Please enter your name.")
        return
    }
    if (!PhoneUtils.isValidIndianMobile(phoneVal)) {
        _uiState.value = AuthUiState.Error("Enter a valid 10-digit Indian mobile number.")
        return
    }
    _phoneNumber.value = phoneVal   // shows +91XXXXXXXXXX on the OTP screen
    _otpError.value = null
    _uiState.value = AuthUiState.Loading

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Instant / auto-retrieved SMS: verify for real
            repository.signInWithPhoneCredential(
                credential,
                onSuccess = { _uiState.value = AuthUiState.OtpVerified },
                onFailure = { _uiState.value = AuthUiState.Error(it.localizedMessage ?: "Verification failed.") }
            )
        }

        override fun onVerificationFailed(e: FirebaseException) {
            val rawMsg = e.localizedMessage ?: "Phone verification failed."
            val errorMsg = if (rawMsg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                "Firebase Phone Auth permission error. Enable the Phone provider and add your SHA-1/SHA-256 fingerprints in Firebase Console."
            } else rawMsg
            _uiState.value = AuthUiState.Error(errorMsg)
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            _verificationId.value = verificationId
            _uiState.value = AuthUiState.OtpSent
        }
    }

    repository.sendOtp(phoneVal, activity, callbacks)
}

    // SIGNUP FLOW: Step 2 -> Verify OTP
    fun verifySignupOtp() {
    val code = _otpCode.value.trim()
    if (code.length < 6) {
        _otpError.value = "Please enter the 6-digit OTP code."
        return
    }
    _otpError.value = null
    _isVerifyingOtp.value = true

    val credential = PhoneAuthProvider.getCredential(_verificationId.value, code)
    repository.signInWithPhoneCredential(
        credential,
        onSuccess = {
            _isVerifyingOtp.value = false
            _uiState.value = AuthUiState.OtpVerified
        },
        onFailure = {
            _isVerifyingOtp.value = false
            _otpError.value = "Incorrect or expired OTP. Please try again."
        }
    )
}

    // SIGNUP FLOW: Step 3 -> Set Password & Create Account
    fun completeSignupWithPassword() {
        val nameVal = _name.value.trim()
        val phoneVal = _phoneNumber.value.trim()
        val passVal = _password.value
        val confirmVal = _confirmPassword.value

        if (passVal.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters long.")
            return
        }
        if (passVal != confirmVal) {
            _uiState.value = AuthUiState.Error("Passwords do not match.")
            return
        }

        _uiState.value = AuthUiState.Loading

        repository.createAccountWithPassword(
            name = nameVal,
            phone = phoneVal,
            password = passVal,
            onSuccess = { user ->
                _uiState.value = AuthUiState.Success(user)
            },
            onFailure = { ex ->
                _uiState.value = AuthUiState.Error(ex.localizedMessage ?: "Account creation failed.")
            }
        )
    }

    // LOGIN FLOW: Name + Password (with phone disambiguation if needed)
    fun loginWithPassword() {
        val nameVal = _name.value.trim()
        val passVal = _password.value
        val disambigPhone = _disambiguationPhone.value.trim().ifEmpty { null }

        if (nameVal.isEmpty() || passVal.isEmpty()) {
            _uiState.value = AuthUiState.Error(AuthRepository.GENERIC_AUTH_ERROR)
            return
        }

        _uiState.value = AuthUiState.Loading

        repository.loginWithNameAndPassword(
            name = nameVal,
            password = passVal,
            phoneDisambiguation = disambigPhone,
            onDisambiguationRequired = {
                _uiState.value = AuthUiState.DisambiguationRequired()
            },
            onSuccess = { user ->
                _uiState.value = AuthUiState.Success(user)
            },
            onFailure = { ex ->
                _uiState.value = AuthUiState.Error(ex.localizedMessage ?: AuthRepository.GENERIC_AUTH_ERROR)
            }
        )
    }

    fun checkExistingSession() {
    val currentUser = repository.getCurrentUser()
    if (currentUser != null) {
        if (currentUser.email.isNullOrBlank()) {   // phone verified, signup never finished
            repository.signOut()
            return
        }
        _uiState.value = AuthUiState.Loading
        repository.syncUserDocument(
            uid = currentUser.uid,
            phone = currentUser.phoneNumber ?: "",
            onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
            onFailure = { _uiState.value = AuthUiState.Idle }
        )
    }
}

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

