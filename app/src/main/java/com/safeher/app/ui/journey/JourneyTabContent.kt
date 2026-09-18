package com.safeher.app.ui.journey
import com.safeher.app.data.model.LocationData

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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.User
import com.safeher.app.ui.sos.SosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyTabContent(
    user: User,
    currentLocation: LocationData?,
    viewModel: JourneyViewModel = viewModel(),
    sosViewModel: SosViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(currentLocation) {

    currentLocation?.let { location ->

        viewModel.updateCurrentLocation(
            location.lat,
            location.lng
        )
    }
}
    var searchInput by remember { mutableStateOf(uiState.searchQuery) }

    val originLatLng = remember(uiState.originLat, uiState.originLng) {
        LatLng(uiState.originLat, uiState.originLng)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(originLatLng, 13f)
    }

    // Auto center map camera when destination or origin changes
    LaunchedEffect(uiState.destLat, uiState.destLng) {
        val centerLat = (uiState.originLat + uiState.destLat) / 2.0
        val centerLng = (uiState.originLng + uiState.destLng) / 2.0
        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(centerLat, centerLng), 12.5f)
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
            // ACTIVE JOURNEY HEADER BANNER
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1B5E20),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active Journey Monitoring",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Destination: ${uiState.activeJourney?.destinationAddress ?: uiState.destinationAddress}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Safety Score: ${uiState.activeJourney?.routeScore ?: 0.85} • Polling live every 15s",
                        color = Color.White.copy(alpha = 0.8f),
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
            // TOP SEARCH HEADER (PLANNED JOURNEY MODE)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Plan Safe Journey",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Origin: Current Location",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = {
                                searchInput = it
                                viewModel.updateSearchQuery(it)
                            },
                            placeholder = { Text("Enter destination address or place...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.searchAndScoreRoutes(searchInput) },
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Search")
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

                    if (uiState.isJourneySaved) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Journey saved to Firestore! Ready to start monitoring.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                        }
                    }
                }
            }
        }

        // MAIN GOOGLE MAP VIEW
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = true)
            ) {
                // Origin Marker
                Marker(
                    state = MarkerState(position = originLatLng),
                    title = "Origin (You)",
                    snippet = "Start Location",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )

                // Current Live Ping Marker (when active)
                if (uiState.isJourneyActive && uiState.currentPingLat != 0.0) {
                    val currentPingLatLng = LatLng(uiState.currentPingLat, uiState.currentPingLng)
                    Marker(
                        state = MarkerState(position = currentPingLatLng),
                        title = "Live GPS Location",
                        snippet = if (uiState.isDeviated) "⚠️ Off Route Path" else "✓ On Route Path",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (uiState.isDeviated) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_GREEN
                        )
                    )
                }

                // Destination Marker
                if (uiState.routes.isNotEmpty() || uiState.activeJourney != null) {
                    val destLatLng = LatLng(uiState.destLat, uiState.destLng)
                    Marker(
                        state = MarkerState(position = destLatLng),
                        title = "Destination",
                        snippet = uiState.destinationAddress,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }

                // Dark Spot Warning Markers on Map
                uiState.routes.forEach { route ->
                    route.darkSpots.forEach { spot ->
                        Marker(
                            state = MarkerState(position = LatLng(spot.lat, spot.lng)),
                            title = "⚠️ Low Light / Dark Stretch",
                            snippet = "Caution: Low ambient lighting area along ${route.name}",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                        )
                    }
                }

                // Route Polylines
                uiState.routes.forEachIndexed { index, route ->
                    val isSelected = (route.routeId == uiState.selectedRoute?.routeId)
                    val points = route.points.map { LatLng(it.lat, it.lng) }

                    val polylineColor = when {
                        route.compositeScore >= 0.70 -> Color(0xFF2E7D32)
                        route.compositeScore >= 0.45 -> Color(0xFFF57F17)
                        else -> Color(0xFFC62828)
                    }

                    Polyline(
                        points = points,
                        color = if (isSelected) polylineColor else polylineColor.copy(alpha = 0.5f),
                        width = if (isSelected) 14f else 8f,
                        onClick = { if (!uiState.isJourneyActive) viewModel.selectRoute(route) }
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // BOTTOM ROUTE SELECTION AND START JOURNEY ACTIONS (PLANNED MODE ONLY)
        if (!uiState.isJourneyActive) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {

                    // Mandatory Ethical Disclaimer Notice Banner
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Disclaimer",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Composite Safety = NASA Lighting + Traffic Congestion + Crowd Density.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    if (uiState.routes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Search a destination above to evaluate route safety scores.",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
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

                        Spacer(modifier = Modifier.height(10.dp))

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
                                    text = "Start Active Journey Monitoring",
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
fun RouteCardItem(
    route: RouteOption,
    index: Int,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val badgeColor = when {
        route.compositeScore >= 0.70 -> Color(0xFF2E7D32)
        route.compositeScore >= 0.45 -> Color(0xFFF57F17)
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
                        text = "Score: ${route.compositeScore}",
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
                    text = "Light: ${(route.lightingScore * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.outline
                )
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
