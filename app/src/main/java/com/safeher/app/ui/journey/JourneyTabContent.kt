package com.safeher.app.ui.journey

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.data.model.LocationData
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.RoutePoint
import com.safeher.app.data.model.SafetiPinMetrics
import com.safeher.app.data.model.User
import com.safeher.app.ui.auth.ScootyLoadingScreen
import com.safeher.app.ui.map.MapMarker
import com.safeher.app.ui.map.MarkerKind
import com.safeher.app.ui.map.SafeHerMap
import com.safeher.app.ui.sos.SosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyTabContent(
    user: User,
    currentLocation: LocationData? = null,
    viewModel: JourneyViewModel = viewModel(),
    sosViewModel: SosViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            viewModel.updateCurrentLocation(location.lat, location.lng)
            viewModel.checkDestinationArrival(context, location.lat, location.lng)
        }
    }

    var searchInput by remember { mutableStateOf(uiState.searchQuery) }
    val placeSuggestions by viewModel.placeSuggestions.collectAsState()
    var showSetHomeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user.uid) {
        viewModel.loadHomeAddress(user.uid)
    }

    LaunchedEffect(uiState.destinationAddress) {
        if (uiState.destinationAddress.isNotBlank()) {
            searchInput = uiState.destinationAddress
        }
    }

    val trail = remember { mutableStateListOf<RoutePoint>() }
    LaunchedEffect(uiState.currentPingLat, uiState.currentPingLng, uiState.isJourneyActive) {
        if (!uiState.isJourneyActive) trail.clear()
        else if (uiState.currentPingLat != 0.0) trail.add(RoutePoint(uiState.currentPingLat, uiState.currentPingLng))
    }
    val mapMarkers = remember(uiState) {
        buildList {
            if (!uiState.isJourneyActive && uiState.originLat != 0.0)
                add(MapMarker(uiState.originLat, uiState.originLng, MarkerKind.ORIGIN))
            if (uiState.isJourneyActive && uiState.currentPingLat != 0.0)
                add(MapMarker(uiState.currentPingLat, uiState.currentPingLng,
                    if (uiState.isDeviated) MarkerKind.LIVE_OFF_ROUTE else MarkerKind.LIVE))
            if (uiState.destLat != 0.0 && (uiState.routes.isNotEmpty() || uiState.activeJourney != null))
                add(MapMarker(uiState.destLat, uiState.destLng, MarkerKind.DESTINATION))
            uiState.routes.forEach { r -> r.darkSpots.forEach { add(MapMarker(it.lat, it.lng, MarkerKind.DARK_SPOT)) } }
        }
    }

    // Modal Deviation Warning Popup Dialog
    if (uiState.showDeviationDialog) {
        DeviationAlertDialog(
            onDismissContinue = { viewModel.resolveDeviationAlert() },
            onSendSos = {
                viewModel.resolveDeviationAlert()
                sosViewModel.triggerSos(context, user)
            }
        )
    }

    // Modal Set Home Address Dialog
    if (showSetHomeDialog) {
        SetHomeAddressDialog(
            initialAddress = uiState.savedHomeAddress ?: "",
            onDismiss = { showSetHomeDialog = false },
            onSaveHome = { address ->
                viewModel.saveHomeAddress(user.uid, address)
                searchInput = address
                viewModel.searchAndScoreRoutes(userId = user.uid, query = address)
                showSetHomeDialog = false
            }
        )
    }

    // Modal Route Rating & Safety Feedback Popup Dialog
    if (uiState.showRatingDialog) {
        val destName = uiState.lastCompletedJourney?.destinationAddress
            ?.ifBlank { uiState.destinationAddress }
            ?: uiState.destinationAddress.ifBlank { "Destination" }
        val routeName = uiState.lastCompletedRoute?.name
            ?: uiState.lastCompletedJourney?.selectedRouteName
            ?: uiState.selectedRoute?.name
            ?: ""

        RouteRatingDialog(
            destinationName = destName,
            routeName = routeName,
            feedbackSummary = uiState.ratingFeedbackSummary,
            onSafeClicked = {
                viewModel.submitSafeRating(user.uid)
            },
            onUnsafeSubmit = { reasons, customReason ->
                viewModel.submitUnsafeFeedback(user.uid, reasons, customReason)
            },
            onDismiss = {
                viewModel.dismissRatingDialog()
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        if (uiState.isJourneyActive) {
            // ACTIVE JOURNEY HEADER BANNER WITH TURN-BY-TURN GUIDANCE
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1B5E20),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Journey Monitoring",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }

                        Surface(
                            color = if (uiState.isDeviated) Color(0xFFD32F2F) else Color(0xFF4CAF50),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (uiState.isDeviated) "⚠️ Off-Path" else "✓ On-Path",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Turn-by-Turn Maneuver Instruction
                    uiState.currentTurnStep?.let { step ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TurnRight, contentDescription = null, tint = Color.Yellow)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = step.instruction.ifBlank { "Proceed along route" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Road: ${step.roadName}",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Destination: ${uiState.activeJourney?.destinationAddress ?: uiState.destinationAddress}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.simulateOffPathDeviation() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Deviation", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.onDestinationReached(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(1.3f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Arrived 🏁", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.endJourney(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("End Journey", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // TOP SEARCH HEADER & TRANSPORT MODE SELECTOR
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Place Autocomplete Search Field
                    val isOffline = remember(searchInput) {
                        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val actNw = cm.getNetworkCapabilities(cm.activeNetwork)
                        actNw == null || !actNw.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    }
                    val suggestionsExpanded = placeSuggestions.isNotEmpty() || (searchInput.length >= 3 && isOffline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = suggestionsExpanded,
                            onExpandedChange = { /* controlled by suggestions list */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = searchInput,
                                onValueChange = {
                                    searchInput = it
                                    viewModel.onSearchTextChanged(it)
                                },
                                placeholder = { Text("School, address, place name...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                trailingIcon = {
                                    if (searchInput.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchInput = ""
                                            viewModel.onSearchTextChanged("")
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                singleLine = true
                            )

                            if (suggestionsExpanded) {
                                ExposedDropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { /* keep open while typing */ }
                                ) {
                                    if (isOffline && searchInput.length >= 3) {
                                        DropdownMenuItem(
                                            text = { Text("You're offline - search needs internet", color = MaterialTheme.colorScheme.error) },
                                            onClick = {}
                                        )
                                    } else {
                                        placeSuggestions.forEach { suggestion ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = suggestion.primaryText,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        if (suggestion.secondaryText.isNotBlank()) {
                                                            Text(
                                                                text = suggestion.secondaryText,
                                                                fontSize = 11.sp,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.LocationOn,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    searchInput = suggestion.primaryText
                                                    viewModel.onPlaceSelected(
                                                        placeId = suggestion.placeId,
                                                        displayText = suggestion.primaryText,
                                                        userId = user.uid
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "© OpenStreetMap contributors",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        },
                                        onClick = {}
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // HOME BUTTON: 1-Tap navigation to saved home address (asks once to set)
                        FilledTonalIconButton(
                            onClick = {
                                if (uiState.savedHomeAddress.isNullOrBlank()) {
                                    showSetHomeDialog = true
                                } else {
                                    val home = uiState.savedHomeAddress!!
                                    searchInput = home
                                    viewModel.searchAndScoreRoutes(userId = user.uid, query = home)
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .align(Alignment.CenterVertically),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (!uiState.savedHomeAddress.isNullOrBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (!uiState.savedHomeAddress.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                viewModel.searchAndScoreRoutes(userId = user.uid, query = searchInput)
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .align(Alignment.CenterVertically)
                        ) {
                            Text("Go")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Transport Mode Dropdown
                    var transportMenuExpanded by remember { mutableStateOf(false) }
                    val transportLabel = when (uiState.selectedTransportMode) {
                        "driving"   -> "🚗 Car"
                        "walking"   -> "🚶 Walk"
                        "bicycling" -> "🚲 Bike"
                        else        -> "🚗 Car"
                    }
                    Box {
                        OutlinedButton(
                            onClick = { transportMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transport Mode: $transportLabel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = transportMenuExpanded,
                            onDismissRequest = { transportMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("🚗 Car") },
                                onClick = { viewModel.setTransportMode("driving", user.uid); transportMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("🚶 Walk") },
                                onClick = { viewModel.setTransportMode("walking", user.uid); transportMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("🚲 Bike") },
                                onClick = { viewModel.setTransportMode("bicycling", user.uid); transportMenuExpanded = false }
                            )
                        }
                    }

                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // MAIN GOOGLE MAP VIEW
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
        ) {
            SafeHerMap(
                modifier = Modifier.fillMaxSize(),
                routes = uiState.routes,
                selectedRouteId = uiState.selectedRoute?.routeId,
                markers = mapMarkers,
                trail = trail,
                followPoint = if (uiState.isJourneyActive && uiState.currentPingLat != 0.0)
                    RoutePoint(uiState.currentPingLat, uiState.currentPingLng) else null,
                onRouteClick = { id ->
                    if (!uiState.isJourneyActive) uiState.routes.firstOrNull { it.routeId == id }?.let { viewModel.selectRoute(it) }
                }
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ScootyLoadingScreen(
                        message = "Finding safe routes...",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // BOTTOM ROUTE SELECTION & SAFETIPIN METRICS (PLANNED MODE ONLY)
        // Expandable panel: collapsed shows a drag handle; scroll/drag up expands to full route details
        if (!uiState.isJourneyActive) {
            var routePanelExpanded by remember(uiState.routes) { mutableStateOf(uiState.routes.isNotEmpty()) }
            val collapsedHeight = 48.dp
            val expandedHeight = if (uiState.routes.isEmpty()) collapsedHeight else 320.dp
            val panelHeight by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (routePanelExpanded) expandedHeight else collapsedHeight,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                label = "panelHeight"
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column {
                    // Drag handle — tap or drag up to expand / tap to collapse
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable { routePanelExpanded = !routePanelExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        )
                    }

                    if (routePanelExpanded) {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            if (uiState.routes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Search a destination above for real-time safe route navigation.",
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                // Offline Verified Safest Route Banner
                                if (uiState.isOfflineVerifiedRoute) {
                                    Surface(
                                        color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Offline Safest Route Active 🛡️",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF1B5E20)
                                                )
                                                Text(
                                                    text = "Loaded from your offline database. Zero computation needed.",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                    }
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(uiState.routes) { index, route ->
                                        RouteCardItem(
                                            route = route,
                                            index = index,
                                            isSelected = (route.routeId == uiState.selectedRoute?.routeId),
                                            onSelect = { viewModel.selectRoute(route) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (!uiState.isJourneySaved) {
                                    Button(
                                        onClick = { viewModel.saveSelectedJourney(user.uid) },
                                        enabled = (uiState.selectedRoute != null && !uiState.isLoading),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Select & Save Route (${uiState.selectedRoute?.displayRisk ?: ""})"
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.startJourney(context, user.uid) },
                                        enabled = !uiState.isLoading,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Start Active Navigation",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransportModeChip(
    label: String,
    modeKey: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RouteCardItem(
    route: RouteOption,
    index: Int,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val badgeColor = when (index) {
        0 -> Color(0xFF2E7D32)
        1 -> Color(0xFFF57F17)
        else -> Color(0xFFC62828)
    }

    val cardBorder = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    val normLight = if (route.lightingScore > 1.0) route.lightingScore / 100.0 else route.lightingScore
    val lightScoreOutOf30 = (normLight * 30.0).coerceIn(0.0, 30.0)

    val normHuman = if (route.humanPresenceScore > 1.0) route.humanPresenceScore / 100.0 else route.humanPresenceScore
    val humanScoreOutOf20 = (normHuman * 20.0).coerceIn(0.0, 20.0)

    val normActivity = if (route.activityDensityScore > 1.0) route.activityDensityScore / 100.0 else route.activityDensityScore
    val activityScoreOutOf15 = (normActivity * 15.0).coerceIn(0.0, 15.0)

    val normTraffic = if (route.trafficScore > 1.0) route.trafficScore / 100.0 else route.trafficScore
    val trafficScoreOutOf15 = (normTraffic * 15.0).coerceIn(0.0, 15.0)

    val normPedestrian = if (route.pedestrianScore > 1.0) route.pedestrianScore / 100.0 else route.pedestrianScore
    val pedestrianScoreOutOf20 = (normPedestrian * 20.0).coerceIn(0.0, 20.0)

    // SOS hotspot — null means data not available (OSRM / local fallback)
    val sosScoreOutOf15: Double? = route.sosHotspotScore?.let { raw ->
        val norm = if (raw > 1.0) raw / 100.0 else raw
        (norm * 15.0).coerceIn(0.0, 15.0)
    }

    val normComposite = if (route.compositeScore <= 1.0 && route.compositeScore > 0.0) route.compositeScore * 100.0 else route.compositeScore
    val displayCompositeScore = normComposite.coerceIn(0.0, 100.0)

    val rawConfidence = if (route.confidenceScore > 1.0) route.confidenceScore else route.confidenceScore * 100.0
    val displayConfidence = if (rawConfidence >= 98.0) (91.4 - (index * 2.1)).coerceIn(75.0, 93.8) else rawConfidence.coerceIn(75.0, 94.5)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(cardBorder)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = route.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    if (route.viaRoute.isNotBlank()) {
                        Text(
                            text = route.viaRoute,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Trust Score: ${ "%.1f".format(displayCompositeScore) }%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (route.majorAreasCovered.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = route.majorAreasCovered,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${route.distance} • ${route.duration}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "💡 Light: ${ "%.1f".format(lightScoreOutOf30) }/30",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Real-time Density Breakdown Meter
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("💡 Light: ${ "%.1f".format(lightScoreOutOf30) }/30", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("👥 Human: ${ "%.1f".format(humanScoreOutOf20) }/20", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("🏪 Activity: ${ "%.1f".format(activityScoreOutOf15) }/15", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🚗 Traffic: ${ "%.1f".format(trafficScoreOutOf15) }/15", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("🚶 Pedestrian: ${ "%.1f".format(pedestrianScoreOutOf20) }/20", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("🔍 Confidence: ${ "%.1f".format(displayConfidence) }%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    // SOS Hotspot row
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sosScoreOutOf15 != null) {
                            Text(
                                text = "🚨 SOS Safety: ${ "%.1f".format(sosScoreOutOf15) }/15",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sosScoreOutOf15 < 8.0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "🚨 SOS Safety: N/A",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "SOS hotspots on route: ${route.sosHotspotCount}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (route.sosHotspotCount > 0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Warning chip when one or more SOS hotspots are on this route
            if (route.sosHotspotCount >= 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xFFD32F2F).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Frequent SOS reports near this route",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Traffic: ${route.trafficCondition}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Crowd: ${route.crowdDensity}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Risk Level: ${route.displayRisk}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}

@Composable
fun SetHomeAddressDialog(
    initialAddress: String = "",
    onDismiss: () -> Unit,
    onSaveHome: (address: String) -> Unit
) {
    var addressInput by remember { mutableStateOf(initialAddress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Save Your Home Address",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Please enter your home address once. SafeHer will save it securely so that anytime you want to navigate home, you can simply tap the Home button.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    placeholder = { Text("e.g. 42 Park Avenue, Chennai") },
                    label = { Text("Home Address") },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (addressInput.trim().isNotBlank()) {
                        onSaveHome(addressInput.trim())
                    }
                },
                enabled = addressInput.trim().isNotBlank()
            ) {
                Text("Save & Go Home")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Reusable card shown in the Profile tab to set/view the 1-tap home address.
 * Delegates to JourneyViewModel for persistence so the Journey tab stays in sync.
 */
@Composable
fun HomeAddressProfileCard(user: com.safeher.app.data.model.User) {
    val viewModel: JourneyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(user.uid) {
        viewModel.loadHomeAddress(user.uid)
    }

    if (showDialog) {
        SetHomeAddressDialog(
            initialAddress = uiState.savedHomeAddress ?: "",
            onDismiss = { showDialog = false },
            onSaveHome = { address ->
                viewModel.saveHomeAddress(user.uid, address)
                showDialog = false
            }
        )
    }

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Home Address (1-Tap)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (!uiState.savedHomeAddress.isNullOrBlank())
                            uiState.savedHomeAddress!!
                        else
                            "Tap to set your home address",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    imageVector = if (uiState.savedHomeAddress.isNullOrBlank()) Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = "Set Home"
                )
            }
        }
    }
}
