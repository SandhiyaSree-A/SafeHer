package com.safeher.app.ui.map

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.RoutePoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

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

private fun routeColor(index: Int): String = when (index) {
    0 -> "#00E676"   // Safest – bright green
    1 -> "#FFB300"   // Moderate – amber
    else -> "#FF5252" // High risk – red
}

/**
 * MapLibre-backed map composable that replaces the Google Maps GoogleMap composable.
 *
 * Parameters mirror what JourneyTabContent and HomeTabContent need:
 *  - routes / selectedRouteId   → polylines with colour coding
 *  - markers                    → origin, destination, live, dark-spot pins
 *  - trail                      → live breadcrumb polyline drawn in blue
 *  - followPoint                → if set the camera tracks this coordinate
 *  - onRouteClick               → invoked when the user taps a route polyline
 */
@SuppressLint("MissingPermission")
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
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleReady = remember { mutableStateOf(false) }

    // Obtain a stable context ref inside the composable lifecycle
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // MapLibre must be initialised before any MapView is created.
            MapLibre.getInstance(ctx)
            val mv = MapView(ctx)
            mapViewRef.value = mv
            mv.onCreate(null)
            mv.getMapAsync { map ->
                mapRef.value = map
                map.setStyle(MapConfig.styleUrl) {
                    styleReady.value = true
                }
            }
            mv
        },
        update = { _ -> /* handled via side-effects below */ }
    )

    // Re-draw whenever data changes (after style is ready)
    LaunchedEffect(styleReady.value, routes, selectedRouteId, markers, trail) {
        val map = mapRef.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        drawMapContent(map, routes, selectedRouteId, markers, trail, onRouteClick)
    }

    // Camera follow
    LaunchedEffect(followPoint) {
        val map = mapRef.value ?: return@LaunchedEffect
        followPoint?.let { pt ->
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(pt.lat, pt.lng))
                        .zoom(16.0)
                        .build()
                ),
                600
            )
        }
    }

    // MapView lifecycle
    DisposableEffect(Unit) {
        mapViewRef.value?.onStart()
        mapViewRef.value?.onResume()
        onDispose {
            mapViewRef.value?.onPause()
            mapViewRef.value?.onStop()
            mapViewRef.value?.onDestroy()
        }
    }
}

private fun drawMapContent(
    map: MapLibreMap,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    markers: List<MapMarker>,
    trail: List<RoutePoint>,
    onRouteClick: (String) -> Unit
) {
    val style = map.style ?: return

    // --- Fit camera to first route bounds when routes are available ---
    if (routes.isNotEmpty()) {
        val allPoints = routes.firstOrNull { it.routeId == selectedRouteId }?.points
            ?: routes.first().points
        if (allPoints.size >= 2) {
            try {
                val builder = LatLngBounds.Builder()
                allPoints.forEach { builder.include(LatLng(it.lat, it.lng)) }
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100), 700)
            } catch (_: Exception) {
                allPoints.lastOrNull()?.let {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 13.0))
                }
            }
        }
    } else if (markers.isNotEmpty()) {
        val m = markers.first()
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(m.lat, m.lng), 15.0), 600)
    }

    // --- Route polylines via the GeoJSON source / layer approach ---
    // We use direct GeoJSON sources so we don't need the annotation plugin for lines.
    // Remove old sources/layers first.
    val layerPrefix = "safeher_route_"
    val trailLayerId = "safeher_trail"
    val trailSourceId = "safeher_trail_src"

    // Remove previously added layers & sources
    try {
        style.layers.filter { it.id.startsWith(layerPrefix) || it.id == trailLayerId }
            .forEach { style.removeLayer(it) }
        style.sources.filter { it.id.startsWith(layerPrefix) || it.id == trailSourceId }
            .forEach { style.removeSource(it) }
    } catch (_: Exception) {}

    // Add route polylines
    routes.forEachIndexed { index, route ->
        if (route.points.size < 2) return@forEachIndexed
        val color = routeColor(index)
        val isSelected = route.routeId == selectedRouteId
        val lineWidth = if (isSelected) 6.0f else 3.5f
        val opacity = if (isSelected) 1.0f else 0.4f

        val coords = route.points.joinToString(",") { "[${it.lng},${it.lat}]" }
        val geojson = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}]}"""

        val srcId = "${layerPrefix}src_${route.routeId}"
        val layId = "${layerPrefix}${route.routeId}"
        try {
            val src = org.maplibre.android.style.sources.GeoJsonSource(srcId, geojson)
            style.addSource(src)
            val layer = org.maplibre.android.style.layers.LineLayer(layId, srcId).apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.lineColor(color),
                    org.maplibre.android.style.layers.PropertyFactory.lineWidth(lineWidth),
                    org.maplibre.android.style.layers.PropertyFactory.lineOpacity(opacity),
                    org.maplibre.android.style.layers.PropertyFactory.lineCap(
                        org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                    ),
                    org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                        org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                    )
                )
            }
            style.addLayer(layer)
        } catch (_: Exception) {}
    }

    // Trail (breadcrumb) line in blue
    if (trail.size >= 2) {
        val coords = trail.joinToString(",") { "[${it.lng},${it.lat}]" }
        val geojson = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}]}"""
        try {
            val src = org.maplibre.android.style.sources.GeoJsonSource(trailSourceId, geojson)
            style.addSource(src)
            val layer = org.maplibre.android.style.layers.LineLayer(trailLayerId, trailSourceId).apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.lineColor("#2196F3"),
                    org.maplibre.android.style.layers.PropertyFactory.lineWidth(4.0f),
                    org.maplibre.android.style.layers.PropertyFactory.lineCap(
                        org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                    )
                )
            }
            style.addLayer(layer)
        } catch (_: Exception) {}
    }

    // --- Markers via MapLibreMap.addMarker ---
    map.markers.forEach { map.removeMarker(it) }
    markers.forEach { m ->
        val opts = org.maplibre.android.annotations.MarkerOptions()
            .position(LatLng(m.lat, m.lng))
            .title(
                when (m.kind) {
                    MarkerKind.ORIGIN -> "Start"
                    MarkerKind.DESTINATION -> "Destination"
                    MarkerKind.LIVE -> "You are here"
                    MarkerKind.LIVE_OFF_ROUTE -> "⚠️ Off Route"
                    MarkerKind.DARK_SPOT -> "⚠️ Dark Spot"
                }
            )
        try { map.addMarker(opts) } catch (_: Exception) {}
    }
}
