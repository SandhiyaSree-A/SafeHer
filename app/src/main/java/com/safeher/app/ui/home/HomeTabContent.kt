package com.safeher.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.safeher.app.data.model.User

@Composable
fun HomeTabContent(
    user: User,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isTrackingActive by viewModel.isTrackingActive.collectAsState()

    var showRationaleDialog by remember { mutableStateOf(false) }

    fun checkPermissionsGranted(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    var hasLocationPermission by remember { mutableStateOf(checkPermissionsGranted()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            hasLocationPermission = true
            viewModel.startLocationUpdates(user.uid)
        } else {
            hasLocationPermission = false
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.startLocationUpdates(user.uid)
        }
    }

    // Default target LatLng
    val targetLatLng = currentLocation?.let { LatLng(it.lat, it.lng) } ?: LatLng(37.7749, -122.4194)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetLatLng, 15f)
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lng), 16f)
            )
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = "Location Safety") },
            title = { Text("Location Permission Required") },
            text = {
                Text(
                    "SafeHer needs access to your device location to enable live tracking and display your location on the map for emergency safety."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationaleDialog = false
                        val permsToRequest = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            permsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                        permissionLauncher.launch(permsToRequest.toTypedArray())
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasLocationPermission) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = true
                )
            ) {
                currentLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = LatLng(loc.lat, loc.lng)),
                        title = "${user.name}'s Location",
                        snippet = "Lat: ${String.format("%.4f", loc.lat)}, Lng: ${String.format("%.4f", loc.lng)}"
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location Disabled",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Location Access Disabled",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please enable location permissions to view your live location on Google Maps.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showRationaleDialog = true }) {
                    Text("Enable Location Tracking")
                }
            }
        }

        // Location Info Banner & Controls
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTrackingActive) "Live Location Active" else "Location Tracking Paused",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isTrackingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    currentLocation?.let { loc ->
                        Text(
                            text = "Lat: ${String.format("%.4f", loc.lat)}, Lng: ${String.format("%.4f", loc.lng)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } ?: Text(
                        text = "Acquiring GPS fix...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(
                    onClick = {
                        if (!hasLocationPermission) {
                            showRationaleDialog = true
                        } else {
                            if (isTrackingActive) {
                                viewModel.stopLocationUpdates()
                            } else {
                                viewModel.startLocationUpdates(user.uid)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Toggle Location",
                        tint = if (isTrackingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
