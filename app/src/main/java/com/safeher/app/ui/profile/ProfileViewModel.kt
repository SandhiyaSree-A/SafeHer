package com.safeher.app.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.model.EmergencyContact
import com.safeher.app.data.model.User
import com.safeher.app.data.repository.AuthRepository
import com.safeher.app.data.repository.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.safeher.app.util.PhoneUtils

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContactRepository()
    private val authRepository = AuthRepository()

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _profileUpdateError = MutableStateFlow<String?>(null)
    val profileUpdateError: StateFlow<String?> = _profileUpdateError.asStateFlow()

    fun updateProfile(
        currentUser: User,
        newName: String,
        newPhone: String,
        onUpdated: (User) -> Unit
    ) {
        _isSavingProfile.value = true
        _profileUpdateError.value = null
        authRepository.updateProfile(
            uid = currentUser.uid,
            name = newName,
            phone = newPhone,
            onSuccess = { updatedUser ->
                _isSavingProfile.value = false
                onUpdated(updatedUser)
            },
            onFailure = { ex ->
                _isSavingProfile.value = false
                _profileUpdateError.value = ex.localizedMessage ?: "Failed to update profile."
            }
        )
    }

    fun clearProfileUpdateError() {
        _profileUpdateError.value = null
    }

    fun loadContacts(userUid: String) {
        if (userUid.isBlank()) return
        _isLoading.value = true
        viewModelScope.launch {
            repository.getEmergencyContacts(userUid).collect { contactList ->
                _contacts.value = contactList
                _isLoading.value = false
            }
        }
    }

    fun addContact(userUid: String, name: String, phone: String, relation: String) {
    val normalizedPhone = PhoneUtils.normalizeIndian(phone.trim())
    if (name.isBlank() || phone.isBlank()) {
        _errorMessage.value = "Name and Phone are required."
        return
    }
    if (!PhoneUtils.isValidPhone(normalizedPhone)) {
        _errorMessage.value = "Enter a valid phone number (10-digit Indian mobile, or +country code)."
        return
    }
    _isLoading.value = true
    repository.addEmergencyContact(
        uid = userUid,
        name = name.trim(),
        phone = normalizedPhone,          // <- was phone.trim()
        relation = relation.ifBlank { "Emergency Contact" },
        onSuccess = {
            _isLoading.value = false
            _errorMessage.value = null
        },
        onFailure = { ex ->
            _isLoading.value = false
            _errorMessage.value = ex.localizedMessage ?: "Failed to add contact."
        }
    )
}

    fun deleteContact(userUid: String, contactId: String) {
        repository.deleteEmergencyContact(
            uid = userUid,
            contactId = contactId,
            onSuccess = {},
            onFailure = { ex ->
                _errorMessage.value = ex.localizedMessage ?: "Failed to delete contact."
            }
        )
    }

    fun clearError() {
        _errorMessage.value = null
    }
}