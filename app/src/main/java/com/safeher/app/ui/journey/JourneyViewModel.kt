package com.safeher.app.ui.journey

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.safeher.app.data.model.Journey
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.RouteTurnStep
import com.safeher.app.data.repository.RouteScoringRepository
import com.safeher.app.data.repository.RouteRatingRepository
import com.safeher.app.data.repository.UserPreferencesRepository
import com.safeher.app.data.repository.PlacesAutocompleteRepository
import com.safeher.app.data.repository.PlaceSuggestion
import com.safeher.app.data.model.RouteFeedback
import com.safeher.app.util.RouteCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class JourneyUiState(
    val searchQuery: String = "",
    val originLat: Double = 0.0,
    val originLng: Double = 0.0,
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val destinationAddress: String = "",
    val selectedTransportMode: String = "driving", // "driving", "walking", "bicycling"
    val routes: List<RouteOption> = emptyList(),
    val selectedRoute: RouteOption? = null,
    val currentTurnStep: RouteTurnStep? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isJourneySaved: Boolean = false,
    val savedJourneyId: String? = null,
    val activeJourney: Journey? = null,
    val isJourneyActive: Boolean = false,
    val currentPingLat: Double = 0.0,
    val currentPingLng: Double = 0.0,
    val isDeviated: Boolean = false,
    val showDeviationDialog: Boolean = false,
    val showRatingDialog: Boolean = false,
    val lastCompletedJourney: Journey? = null,
    val lastCompletedRoute: RouteOption? = null,
    val savedHomeAddress: String? = null,
    val ratingFeedbackSummary: String? = null,
    val isOfflineVerifiedRoute: Boolean = false
)

class JourneyViewModel(
    private val repository: RouteScoringRepository = RouteScoringRepository(),
    private val ratingRepository: RouteRatingRepository = RouteRatingRepository(),
    private val userPrefsRepository: UserPreferencesRepository = UserPreferencesRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(JourneyUiState())
    val uiState: StateFlow<JourneyUiState> = _uiState.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()
    private var journeyListenerRegistration: ListenerRegistration? = null
    private val placesRepository = PlacesAutocompleteRepository()

    private val _placeSuggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val placeSuggestions: StateFlow<List<PlaceSuggestion>> = _placeSuggestions.asStateFlow()

    fun loadHomeAddress(userId: String) {
        val home = userPrefsRepository.getHomeAddress(userId)
        _uiState.update { it.copy(savedHomeAddress = home) }
    }

    fun saveHomeAddress(userId: String, address: String) {
        viewModelScope.launch {
            userPrefsRepository.saveHomeAddress(userId, address)
            _uiState.update { it.copy(savedHomeAddress = address.trim()) }
        }
    }

    fun updateCurrentLocation(lat: Double, lng: Double) {
        if (lat != 0.0 && lng != 0.0) {
            _uiState.update { it.copy(originLat = lat, originLng = lng) }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private var searchJob: Job? = null

    fun onSearchTextChanged(query: String) {
        updateSearchQuery(query)
        if (query.length < 3) {
            _placeSuggestions.value = emptyList()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            placesRepository.autocomplete(
                query = query,
                biasLat = _uiState.value.originLat.takeIf { it != 0.0 },
                biasLng = _uiState.value.originLng.takeIf { it != 0.0 }
            ).onSuccess { _placeSuggestions.value = it }
             .onFailure { _placeSuggestions.value = emptyList() }
        }
    }

    fun onPlaceSelected(placeId: String, displayText: String, userId: String = "") {
        updateSearchQuery(displayText)
        _placeSuggestions.value = emptyList()
        viewModelScope.launch {
            placesRepository.getPlaceDetails(placeId).onSuccess { details ->
                searchAndScoreRoutesAtCoordinates(details.lat, details.lng, details.formattedAddress, userId)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "Failed to resolve place details")
                }
            }
        }
    }

    fun searchAndScoreRoutesAtCoordinates(lat: Double, lng: Double, address: String, userId: String = "") {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    destinationAddress = address,
                    isJourneySaved = false
                )
            }

            // 1. Check if offline database already has a verified safest route for this destination
            val offlineSavedRoute = ratingRepository.findOfflineSavedRoute(
                userId = userId,
                destinationQuery = address,
                originLat = _uiState.value.originLat,
                originLng = _uiState.value.originLng,
                destLatFallback = lat,
                destLngFallback = lng
            )

            if (offlineSavedRoute != null) {
                val lastPoint = offlineSavedRoute.points.lastOrNull()
                _uiState.update {
                    it.copy(
                        routes = listOf(offlineSavedRoute),
                        selectedRoute = offlineSavedRoute,
                        destLat = lastPoint?.lat ?: lat,
                        destLng = lastPoint?.lng ?: lng,
                        isLoading = false,
                        isOfflineVerifiedRoute = true
                    )
                }
                return@launch
            }

            // 2. destinationQuery left blank + fallback coords supplied => scoreDynamicRoutes
            val result = repository.scoreDynamicRoutes(
                originLat = _uiState.value.originLat,
                originLng = _uiState.value.originLng,
                destinationQuery = "",
                destLatFallback = lat,
                destLngFallback = lng,
                transportMode = _uiState.value.selectedTransportMode
            )

            result.fold(
                onSuccess = { routes ->
                    val firstRoute = routes.firstOrNull()
                    val lastPoint = firstRoute?.points?.lastOrNull()
                    _uiState.update {
                        it.copy(
                            routes = routes,
                            selectedRoute = firstRoute,
                            destLat = lastPoint?.lat ?: lat,
                            destLng = lastPoint?.lng ?: lng,
                            isLoading = false,
                            isOfflineVerifiedRoute = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to score routes"
                        )
                    }
                }
            )
        }
    }

    fun setTransportMode(mode: String, userId: String = "") {
        if (_uiState.value.selectedTransportMode == mode) return
        _uiState.update { it.copy(selectedTransportMode = mode) }
        val target = _uiState.value.destinationAddress.ifBlank { _uiState.value.searchQuery }.trim()
        if (target.isNotBlank()) {
            searchAndScoreRoutes(userId = userId, query = target)
        }
    }

    fun searchAndScoreRoutes(userId: String = "", query: String? = null) {
        val targetQuery = (query ?: _uiState.value.searchQuery).trim()
        if (targetQuery.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a destination name or address.") }
            return
        }

        if (query == null) {
            val firstSuggestion = _placeSuggestions.value.firstOrNull()
            if (firstSuggestion != null) {
                onPlaceSelected(firstSuggestion.placeId, firstSuggestion.primaryText, userId)
                return
            }
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

            // 1. FAST PATH: Check the offline database table for a pre-saved, verified safest route!
            val offlineSavedRoute = ratingRepository.findOfflineSavedRoute(
                userId = userId,
                destinationQuery = targetQuery,
                originLat = _uiState.value.originLat,
                originLng = _uiState.value.originLng,
                destLatFallback = _uiState.value.destLat,
                destLngFallback = _uiState.value.destLng
            )

            if (offlineSavedRoute != null) {
                val lastPoint = offlineSavedRoute.points.lastOrNull()
                _uiState.update {
                    it.copy(
                        routes = listOf(offlineSavedRoute),
                        selectedRoute = offlineSavedRoute,
                        destLat = lastPoint?.lat ?: it.destLat,
                        destLng = lastPoint?.lng ?: it.destLng,
                        isLoading = false,
                        isOfflineVerifiedRoute = true
                    )
                }
                return@launch
            }

            // 2. Normal computation when no verified offline route exists yet
            val result = repository.scoreDynamicRoutes(
                originLat = _uiState.value.originLat,
                originLng = _uiState.value.originLng,
                destinationQuery = targetQuery,
                transportMode = _uiState.value.selectedTransportMode
            )

            result.fold(
                onSuccess = { routes ->
                    val firstRoute = routes.firstOrNull()
                    val lastPoint = firstRoute?.points?.lastOrNull()
                    _uiState.update {
                        it.copy(
                            routes = routes,
                            selectedRoute = firstRoute,
                            destLat = lastPoint?.lat ?: it.destLat,
                            destLng = lastPoint?.lng ?: it.destLng,
                            isLoading = false,
                            isOfflineVerifiedRoute = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to score routes"
                        )
                    }
                }
            )
        }
    }

    fun selectRoute(route: RouteOption) {
        val firstStep = route.turnSteps.firstOrNull()
        _uiState.update { it.copy(selectedRoute = route, currentTurnStep = firstStep) }
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
            _uiState.value.selectedRoute?.let { route ->
                withContext(Dispatchers.IO) { RouteCache.save(context, journeyId, route.points) }
            }
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
                
                val wasActive = _uiState.value.isJourneyActive
                val isNowCompleted = (journey.status == "completed")

                _uiState.update { current ->
                    current.copy(
                        activeJourney = if (isNowCompleted) null else journey,
                        isJourneyActive = (journey.status == "active"),
                        currentPingLat = journey.currentLat,
                        currentPingLng = journey.currentLng,
                        isDeviated = journey.isDeviated,
                        showDeviationDialog = journey.deviationAlertActive,
                        showRatingDialog = if (wasActive && isNowCompleted) true else current.showRatingDialog,
                        lastCompletedJourney = if (wasActive && isNowCompleted) journey else current.lastCompletedJourney,
                        lastCompletedRoute = if (wasActive && isNowCompleted) current.selectedRoute else current.lastCompletedRoute
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

    fun checkDestinationArrival(context: Context, currentLat: Double, currentLng: Double) {
        if (!_uiState.value.isJourneyActive) return
        val destLat = _uiState.value.destLat
        val destLng = _uiState.value.destLng
        if (destLat == 0.0 || destLng == 0.0 || currentLat == 0.0 || currentLng == 0.0) return

        val distanceMeters = FloatArray(1)
        android.location.Location.distanceBetween(currentLat, currentLng, destLat, destLng, distanceMeters)
        if (distanceMeters[0] <= 60.0f) {
            onDestinationReached(context)
        }
    }

    fun onDestinationReached(context: Context) {
        val journeyId = _uiState.value.savedJourneyId ?: _uiState.value.activeJourney?.journeyId
        val journeyToRate = _uiState.value.activeJourney
        val routeToRate = _uiState.value.selectedRoute

        viewModelScope.launch {
            if (journeyId != null) {
                repository.endJourney(journeyId, context)
            }
            _uiState.update {
                it.copy(
                    isJourneyActive = false,
                    showDeviationDialog = false,
                    showRatingDialog = true,
                    lastCompletedJourney = journeyToRate,
                    lastCompletedRoute = routeToRate
                )
            }
        }
    }

    fun endJourney(context: Context) {
        onDestinationReached(context)
    }

    fun submitSafeRating(userId: String) {
        val state = _uiState.value
        val journey = state.lastCompletedJourney ?: state.activeJourney
        val route = state.lastCompletedRoute ?: state.selectedRoute

        viewModelScope.launch {
            val result = ratingRepository.recordSafeRating(
                userId = userId,
                route = route,
                journey = journey,
                originLat = journey?.originLat ?: state.originLat,
                originLng = journey?.originLng ?: state.originLng,
                destLat = journey?.destLat ?: state.destLat,
                destLng = journey?.destLng ?: state.destLng,
                destinationAddress = journey?.destinationAddress ?: state.destinationAddress,
                transportMode = state.selectedTransportMode
            )

            result.fold(
                onSuccess = { res ->
                    val summary = if (res.isOfflineReady) {
                        "🎉 Route verified as your Safest Route (${res.safeRatingCount} times)! This route has been saved to your offline database. Next time you navigate, it loads instantly without needing to compute again."
                    } else {
                        "Safe rating recorded (${res.safeRatingCount}/3)! Rate this route safe 3 times to permanently save it in the offline database for zero-computation navigation."
                    }
                    _uiState.update { it.copy(ratingFeedbackSummary = summary) }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(ratingFeedbackSummary = "Thank you! Your safe route rating has been saved to the database.")
                    }
                }
            )
        }
    }

    fun submitUnsafeFeedback(userId: String, reasons: List<String>, customReason: String) {
        val state = _uiState.value
        val journey = state.lastCompletedJourney ?: state.activeJourney
        val route = state.lastCompletedRoute ?: state.selectedRoute

        val feedback = RouteFeedback(
            journeyId = journey?.journeyId ?: state.savedJourneyId ?: "",
            userId = userId,
            routeId = route?.routeId ?: journey?.selectedRouteId ?: "route_${System.currentTimeMillis()}",
            routeName = route?.name ?: journey?.selectedRouteName ?: "Navigation Route",
            originLat = journey?.originLat ?: state.originLat,
            originLng = journey?.originLng ?: state.originLng,
            destLat = journey?.destLat ?: state.destLat,
            destLng = journey?.destLng ?: state.destLng,
            destinationAddress = journey?.destinationAddress ?: state.destinationAddress,
            transportMode = state.selectedTransportMode,
            wasSafe = false,
            reasons = reasons,
            customReason = customReason,
            submittedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            ratingRepository.saveRouteFeedback(feedback)
            _uiState.update {
                it.copy(
                    ratingFeedbackSummary = "Thank you for alerting us. Your feedback helps protect women across the community."
                )
            }
        }
    }

    fun dismissRatingDialog() {
        _uiState.update {
            it.copy(
                showRatingDialog = false,
                ratingFeedbackSummary = null,
                lastCompletedJourney = null,
                lastCompletedRoute = null,
                savedJourneyId = null,
                activeJourney = null,
                isJourneySaved = false
            )
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
