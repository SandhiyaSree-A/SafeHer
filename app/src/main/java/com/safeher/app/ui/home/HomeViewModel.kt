package com.safeher.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.model.LocationData
import com.safeher.app.data.repository.LocationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocationRepository(application)

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    private var locationJob: Job? = null

    fun startLocationUpdates(userUid: String) {
    if (!hasLocationPermission()) return
    if (locationJob?.isActive == true) return
    _isTrackingActive.value = true

    locationJob = viewModelScope.launch {
        launch {   // instant first fix
            repository.getFreshLocation()?.let { fix ->
                if (_currentLocation.value == null) {
                    _currentLocation.value = fix
                    repository.updateFirestoreLocation(userUid, fix)
                }
            }
        }
        try {      // then continuous real-time updates
            repository.getLocationUpdates().collect { locationData ->
                _currentLocation.value = locationData
                repository.updateFirestoreLocation(userUid, locationData)
            }
        } catch (e: SecurityException) {
            _isTrackingActive.value = false
        }
    }
}

private fun hasLocationPermission(): Boolean {
    val app = getApplication<Application>()
    return ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
        _isTrackingActive.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }
}
