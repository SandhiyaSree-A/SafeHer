package com.safeher.app.ui.journey

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.safeher.app.data.model.Journey
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.repository.RouteScoringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JourneyUiState(
    val searchQuery: String = "",
    val originLat: Double = 28.6139,
    val originLng: Double = 77.2090,
    val destLat: Double = 28.5355,
    val destLng: Double = 77.3910,
    val destinationAddress: String = "",
    val routes: List<RouteOption> = emptyList(),
    val selectedRoute: RouteOption? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isJourneySaved: Boolean = false,
    val savedJourneyId: String? = null,
    val activeJourney: Journey? = null,
    val isJourneyActive: Boolean = false,
    val currentPingLat: Double = 0.0,
    val currentPingLng: Double = 0.0,
    val isDeviated: Boolean = false,
    val showDeviationDialog: Boolean = false
)

class JourneyViewModel(
    private val repository: RouteScoringRepository = RouteScoringRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(JourneyUiState())
    val uiState: StateFlow<JourneyUiState> = _uiState.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()
    private var journeyListenerRegistration: ListenerRegistration? = null

    fun updateCurrentLocation(lat: Double, lng: Double) {
        if (lat != 0.0 && lng != 0.0) {
            _uiState.update { it.copy(originLat = lat, originLng = lng) }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun searchAndScoreRoutes(query: String? = null) {
        val targetQuery = (query ?: _uiState.value.searchQuery).trim()
        if (targetQuery.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a destination name or address.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true, 
                    errorMessage = null,
                    destinationAddress = targetQuery,
                    isJourneySaved = false
                ) 
            }

            val apiKey = com.safeher.app.BuildConfig.MAPS_API_KEY

            // 1. Geocode Destination
            val geocodeResult = repository.geocodeDestination(targetQuery, apiKey)
            val geocoded = geocodeResult.getOrNull()

            val finalDestLat: Double
            val finalDestLng: Double
            val finalDestAddress: String

            if (geocoded != null && geocoded.lat != 0.0 && geocoded.lng != 0.0) {
                finalDestLat = geocoded.lat
                finalDestLng = geocoded.lng
                finalDestAddress = geocoded.formattedAddress
            } else {
                finalDestLat = _uiState.value.originLat + 0.035 + (targetQuery.hashCode() % 100) * 0.0003
                finalDestLng = _uiState.value.originLng + 0.045 + (targetQuery.hashCode() % 100) * 0.0003
                finalDestAddress = targetQuery
            }

            _uiState.update { 
                it.copy(
                    destLat = finalDestLat, 
                    destLng = finalDestLng,
                    destinationAddress = finalDestAddress
                ) 
            }

            // 2. Fetch Directions and Score Routes
            val result = repository.scoreDynamicRoutes(
                originLat = _uiState.value.originLat,
                originLng = _uiState.value.originLng,
                destLat = finalDestLat,
                destLng = finalDestLng,
                destinationQuery = targetQuery,
                apiKey = apiKey
            )

            result.fold(
                onSuccess = { routes ->
                    if (routes.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "No routes found for destination. Try a different query."
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                routes = routes,
                                selectedRoute = routes.firstOrNull(),
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to find or score routes"
                        )
                    }
                }
            )
        }
    }

    fun selectRoute(route: RouteOption) {
        _uiState.update { it.copy(selectedRoute = route) }
    }

    fun saveSelectedJourney(userId: String) {
        val selected = _uiState.value.selectedRoute ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = repository.saveSelectedJourney(
                userId = userId,
                originLat = _uiState.value.originLat,
                originLng = _uiState.value.originLng,
                destinationAddress = _uiState.value.destinationAddress.ifBlank { "Selected Destination" },
                destLat = _uiState.value.destLat,
                destLng = _uiState.value.destLng,
                route = selected
            )

            result.fold(
                onSuccess = { journeyId ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isJourneySaved = true,
                            savedJourneyId = journeyId
                        )
                    }
                    observeJourneyDoc(journeyId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to save journey to Firestore"
                        )
                    }
                }
            )
        }
    }

    fun startJourney(context: Context, userId: String) {
        val journeyId = _uiState.value.savedJourneyId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.startActiveJourney(journeyId, userId, context)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isJourneyActive = true
                        )
                    }
                    observeJourneyDoc(journeyId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to start active journey"
                        )
                    }
                }
            )
        }
    }

    fun observeJourneyDoc(journeyId: String) {
        journeyListenerRegistration?.remove()
        journeyListenerRegistration = firestore.collection("journeys")
            .document(journeyId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val journey = snapshot.toObject(Journey::class.java) ?: return@addSnapshotListener
                
                _uiState.update {
                    it.copy(
                        activeJourney = journey,
                        isJourneyActive = (journey.status == "active"),
                        currentPingLat = journey.currentLat,
                        currentPingLng = journey.currentLng,
                        isDeviated = journey.isDeviated,
                        showDeviationDialog = journey.deviationAlertActive
                    )
                }
            }
    }

    fun resolveDeviationAlert() {
        val journeyId = _uiState.value.savedJourneyId ?: return
        viewModelScope.launch {
            repository.resolveDeviationAlert(journeyId)
            _uiState.update { it.copy(showDeviationDialog = false, isDeviated = false) }
        }
    }

    fun endJourney(context: Context) {
        val journeyId = _uiState.value.savedJourneyId ?: return
        viewModelScope.launch {
            repository.endJourney(journeyId, context)
            _uiState.update {
                it.copy(
                    isJourneyActive = false,
                    savedJourneyId = null,
                    activeJourney = null,
                    showDeviationDialog = false,
                    isJourneySaved = false
                )
            }
        }
    }

    fun simulateOffPathDeviation() {
        val journeyId = _uiState.value.savedJourneyId ?: return
        viewModelScope.launch {
            repository.simulateDeviationAlert(journeyId)
        }
    }

    override fun onCleared() {
        journeyListenerRegistration?.remove()
        super.onCleared()
    }
}
