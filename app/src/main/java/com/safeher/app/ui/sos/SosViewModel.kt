package com.safeher.app.ui.sos

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.model.SosAlert
import com.safeher.app.data.model.User
import com.safeher.app.data.repository.SosRepository
import com.safeher.app.data.offline.ConnectivityRepository
import com.safeher.app.service.SosForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SosRepository()

    private val _activeAlert = MutableStateFlow<SosAlert?>(null)
    val activeAlert: StateFlow<SosAlert?> = _activeAlert.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _offlineSosMessage = MutableStateFlow<String?>(null)
    val offlineSosMessage: StateFlow<String?> = _offlineSosMessage.asStateFlow()

    fun observeActiveAlert(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            repository.getActiveSosAlertFlow(userId).collect { alert ->
                _activeAlert.value = alert
            }
        }
    }

    fun triggerSos(context: Context, user: User) {
        if (!ConnectivityRepository.get(getApplication()).currentlyOnline()) {
            _offlineSosMessage.value = "No internet detected — SOS sent via SMS to your contacts. It will also appear in Security Room once you're back online."
        }
        val intent = Intent(context, SosForegroundService::class.java).apply {
            putExtra(SosForegroundService.EXTRA_USER_ID, user.uid)
            putExtra(SosForegroundService.EXTRA_USER_NAME, user.name)
            putExtra(SosForegroundService.EXTRA_USER_PHONE, user.phone)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun clearOfflineSosMessage() {
        _offlineSosMessage.value = null
    }

    fun resolveAlert(context: Context, alertId: String) {
        if (alertId.isBlank()) return
        _isResolving.value = true
        repository.resolveSosAlert(
            alertId = alertId,
            onSuccess = {
                _isResolving.value = false
                _activeAlert.value = null
                stopSosService(context)
            },
            onFailure = {
                _isResolving.value = false
                stopSosService(context)
            }
        )
    }

    private fun stopSosService(context: Context) {
        val intent = Intent(context, SosForegroundService::class.java).apply {
            action = SosForegroundService.ACTION_STOP_SOS
        }
        context.startService(intent)
    }
}
