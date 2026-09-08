package com.safeher.app.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.model.SosAlert
import com.safeher.app.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AdminRepository()

    private val _activeAlerts = MutableStateFlow<List<SosAlert>>(emptyList())
    val activeAlerts: StateFlow<List<SosAlert>> = _activeAlerts.asStateFlow()

    init {
        observeActiveAlerts()
    }

    private fun observeActiveAlerts() {
        viewModelScope.launch {
            repository.getActiveSosAlertsFlow().collect { alerts ->
                _activeAlerts.value = alerts
            }
        }
    }

    fun acknowledgeAlert(alertId: String, adminUid: String) {
        repository.acknowledgeAlert(alertId, adminUid)
    }

    fun resolveAlert(alertId: String) {
        repository.resolveAlert(alertId)
    }
}
