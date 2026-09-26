package com.safeher.app.ui.journey

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
        }
    }

    var searchInput by remember { mutableStateOf(uiState.searchQuery) }
    val placeSuggestions by viewModel.placeSuggestions.collectAsState()

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

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.simulateOffPathDeviation() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simulate Deviation", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.endJourney(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("End Journey", fontSize = 12.sp)
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
                    Text(
                        text = "Plan Real-Time Safe Journey",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))


                    // Place Autocomplete Search Field
                    val suggestionsExpanded = placeSuggestions.isNotEmpty()
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
                                    placeSuggestions.forEach { suggestion ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = suggestion.primaryText,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                    if (suggestion.secondaryText.isNotBlank()) {
                                                        Text(
                                                            text = suggestion.secondaryText,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Place,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                searchInput = suggestion.primaryText
                                                viewModel.onPlaceSelected(
                                                    placeId = suggestion.placeId,
                                                    displayText = suggestion.primaryText
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.searchAndScoreRoutes(searchInput)
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .align(Alignment.CenterVertically)
                        ) {
                            Text("Go")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Transport Mode Selector Chips (Drive, Walk, Bike)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TransportModeChip(
                            label = "Car 🚗",
                            modeKey = "driving",
                            isSelected = (uiState.selectedTransportMode == "driving"),
                            onClick = { viewModel.setTransportMode("driving") },
                            modifier = Modifier.weight(1f)
                        )
                        TransportModeChip(
                            label = "Walk 🚶",
                            modeKey = "walking",
                            isSelected = (uiState.selectedTransportMode == "walking"),
                            onClick = { viewModel.setTransportMode("walking") },
                            modifier = Modifier.weight(1f)
                        )
                        TransportModeChip(
                            label = "Bike 🚲",
                            modeKey = "bicycling",
                            isSelected = (uiState.selectedTransportMode == "bicycling"),
                            onClick = { viewModel.setTransportMode("bicycling") },
                            modifier = Modifier.weight(1f)
                        )
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
        if (!uiState.isJourneyActive) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {

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
                        text = "Score: ${ "%.1f".format(route.compositeScore) }%",
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
                    text = "💡 Light: ${ "%.1f".format(route.lightingScore * 100) }%",
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
                        Text("💡 Light: ${ "%.1f".format(route.lightingScore * 100) }%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("👥 Human: ${ "%.1f".format(route.humanPresenceScore * 100) }%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("🏪 Activity: ${ "%.1f".format(route.activityDensityScore * 100) }%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🚗 Traffic: ${ "%.1f".format(route.trafficScore * 100) }%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("🚶 Pedestrian: ${ "%.1f".format(route.pedestrianScore * 100) }%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("🔍 Confidence: ${ "%.1f".format(route.confidenceScore * 100) }%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
