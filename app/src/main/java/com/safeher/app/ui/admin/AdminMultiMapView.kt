package com.safeher.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.safeher.app.data.model.SosAlert

@Composable
fun AdminMultiMapView(
    alerts: List<SosAlert>,
    onSelectAlert: (SosAlert) -> Unit
) {
    // Default center if no alerts
    val defaultCenter = if (alerts.isNotEmpty()) {
        LatLng(alerts.first().lat, alerts.first().lng)
    } else {
        LatLng(13.0827, 80.2707)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 12f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false
            )
        ) {
            alerts.forEach { alert ->
                Marker(
                    state = MarkerState(position = LatLng(alert.lat, alert.lng)),
                    title = "SOS: ${alert.userName}",
                    snippet = "Status: ${alert.status} | Phone: ${alert.userPhone}",
                    onInfoWindowClick = {
                        onSelectAlert(alert)
                    }
                )
            }
        }

        Surface(
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Text(
                text = "Showing ${alerts.size} Active Emergency Pins",
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}
