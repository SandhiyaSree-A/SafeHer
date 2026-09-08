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

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocationRepository(application)

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    private var locationJob: Job? = null

    fun startLocationUpdates(userUid: String) {
        if (locationJob != null && locationJob?.isActive == true) return
        _isTrackingActive.value = true

        locationJob = viewModelScope.launch {
            repository.getLocationUpdates().collect { locationData ->
                _currentLocation.value = locationData
                repository.updateFirestoreLocation(userUid, locationData)
            }
        }
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
