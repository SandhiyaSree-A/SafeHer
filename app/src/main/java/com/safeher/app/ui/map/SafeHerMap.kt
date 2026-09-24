package com.safeher.app.ui.map

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.RoutePoint

// --- Data types consumed by SafeHerMap ---

enum class MarkerKind {
    ORIGIN,
    DESTINATION,
    LIVE,
    LIVE_OFF_ROUTE,
    DARK_SPOT
}

data class MapMarker(
    val lat: Double,
    val lng: Double,
    val kind: MarkerKind
)

// --- Route color helpers ---

private fun routeColor(index: Int): Color = when (index) {
    0 -> Color(0xFF00E676)   // Safest – bright green
    1 -> Color(0xFFFFB300)   // Moderate – amber
    else -> Color(0xFFFF5252) // High risk – red
}

private fun markerHue(kind: MarkerKind): Float = when (kind) {
    MarkerKind.ORIGIN -> BitmapDescriptorFactory.HUE_AZURE
    MarkerKind.DESTINATION -> BitmapDescriptorFactory.HUE_RED
    MarkerKind.LIVE -> BitmapDescriptorFactory.HUE_GREEN
    MarkerKind.LIVE_OFF_ROUTE -> BitmapDescriptorFactory.HUE_ORANGE
    MarkerKind.DARK_SPOT -> BitmapDescriptorFactory.HUE_VIOLET
}

private fun markerTitle(kind: MarkerKind): String = when (kind) {
    MarkerKind.ORIGIN -> "Start"
    MarkerKind.DESTINATION -> "Destination"
    MarkerKind.LIVE -> "You are here"
    MarkerKind.LIVE_OFF_ROUTE -> "⚠️ Off Route"
    MarkerKind.DARK_SPOT -> "⚠️ Dark Spot"
}

/**
 * Google Maps–backed map composable (replaces the earlier MapLibre/MapTiler
 * implementation) so routes render on real Google road/satellite tiles and
 * benefit from the same MAPS_API_KEY already used on the Admin screens.
 *
 *  - routes / selectedRouteId   → polylines with colour coding
 *  - markers                    → origin, destination, live, dark-spot pins
 *  - trail                      → live breadcrumb polyline drawn in blue
 *  - followPoint                → if set the camera tracks this coordinate
 *  - onRouteClick                → invoked when the user taps a route polyline
 */
@Composable
fun SafeHerMap(
    modifier: Modifier = Modifier,
    routes: List<RouteOption> = emptyList(),
    selectedRouteId: String? = null,
    markers: List<MapMarker> = emptyList(),
    trail: List<RoutePoint> = emptyList(),
    followPoint: RoutePoint? = null,
    onRouteClick: (String) -> Unit = {}
) {
    val defaultCenter = LatLng(13.0827, 80.2707) // Chennai fallback until data arrives
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 13f)
    }

    // Fit camera to the selected (or first) route's bounds whenever routes change.
    LaunchedEffect(routes, selectedRouteId) {
        val activeRoute = routes.firstOrNull { it.routeId == selectedRouteId } ?: routes.firstOrNull()
        val pts = activeRoute?.points
        if (pts != null && pts.size >= 2) {
            try {
                val boundsBuilder = LatLngBounds.Builder()
                pts.forEach { boundsBuilder.include(LatLng(it.lat, it.lng)) }
                cameraPositionState.animate(
                    update = com.google.android.gms.maps.CameraUpdateFactory
                        .newLatLngBounds(boundsBuilder.build(), 100),
                    durationMs = 700
                )
            } catch (_: Exception) {
                pts.lastOrNull()?.let {
                    cameraPositionState.animate(
                        update = com.google.android.gms.maps.CameraUpdateFactory
                            .newLatLngZoom(LatLng(it.lat, it.lng), 13f)
                    )
                }
            }
        } else if (markers.isNotEmpty()) {
            val m = markers.first()
            cameraPositionState.animate(
                update = com.google.android.gms.maps.CameraUpdateFactory
                    .newLatLngZoom(LatLng(m.lat, m.lng), 15f),
                durationMs = 600
            )
        }
    }

    // Camera follow (navigation mode)
    LaunchedEffect(followPoint) {
        followPoint?.let { pt ->
            cameraPositionState.animate(
                update = com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(pt.lat, pt.lng))
                        .zoom(18f)
                        .tilt(45f) // 3D tilt for navigation driving feel
                        .build()
                ),
                durationMs = 600
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)
    ) {
        // Route polylines
        routes.forEachIndexed { index, route ->
            if (route.points.size < 2) return@forEachIndexed
            val isSelected = route.routeId == selectedRouteId
            Polyline(
                points = route.points.map { LatLng(it.lat, it.lng) },
                color = routeColor(index),
                width = if (isSelected) 16f else 9f,
                clickable = true,
                onClick = { onRouteClick(route.routeId) }
            )
        }

        // Trail (breadcrumb) line
        if (trail.size >= 2) {
            Polyline(
                points = trail.map { LatLng(it.lat, it.lng) },
                color = Color(0xFF2196F3),
                width = 10f
            )
        }

        // Markers
        markers.forEach { m ->
            Marker(
                state = MarkerState(position = LatLng(m.lat, m.lng)),
                title = markerTitle(m.kind),
                icon = BitmapDescriptorFactory.defaultMarker(markerHue(m.kind))
            )
        }
    }
}