package com.safeher.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.safeher.app.R
import com.safeher.app.data.model.RoutePoint
import com.safeher.app.data.model.User
import com.safeher.app.ui.map.MapMarker
import com.safeher.app.ui.map.MarkerKind
import com.safeher.app.ui.map.SafeHerMap

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

    val lifecycleOwner = LocalLifecycleOwner.current
    var isGpsEnabled by remember {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
        } else {
            val permsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            permissionLauncher.launch(permsToRequest.toTypedArray())
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
        if (hasLocationPermission && isGpsEnabled) {
            SafeHerMap(
                modifier = Modifier.fillMaxSize(),
                routes = emptyList(),
                selectedRouteId = null,
                markers = listOfNotNull(currentLocation?.let { MapMarker(it.lat, it.lng, MarkerKind.LIVE) }),
                trail = emptyList(),
                followPoint = currentLocation?.let { RoutePoint(it.lat, it.lng) }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (!hasLocationPermission) Icons.Default.LocationOff else Icons.Default.LocationOn,
                    contentDescription = "Location Disabled",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (!hasLocationPermission) {
                    Text(
                        text = "Location Permission Required",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please grant location permissions to use the map and live tracking features.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { 
                        val permsToRequest = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        permissionLauncher.launch(permsToRequest.toTypedArray())
                    }) {
                        Text("Grant Permission")
                    }
                } else {
                    Text(
                        text = "Location is Turned Off",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your device's location services are disabled. Please turn them on to enable tracking.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { 
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) {
                        Text("Turn On Location")
                    }
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
                        } else if (!isGpsEnabled) {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
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
