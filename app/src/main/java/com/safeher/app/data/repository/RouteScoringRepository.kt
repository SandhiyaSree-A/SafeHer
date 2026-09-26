package com.safeher.app.data.repository

import com.google.firebase.FirebaseApp
import com.safeher.app.data.offline.OfflineSyncRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import com.safeher.app.data.model.Journey
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class GeocodedLocation(
    val lat: Double,
    val lng: Double,
    val formattedAddress: String
)

class RouteScoringRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val offlineSync = OfflineSyncRepository.get(FirebaseApp.getInstance().applicationContext)
    private val googleDirections = GoogleDirectionsRepository()
    private val disclaimerText = "Risk-awareness estimate for prototype/demo purposes only, not a guarantee of real-world safety or crime prediction"

    /**
     * Scores routes given origin coordinates and a destination query or coordinates.
     * Integrates NASA lighting analysis, Traffic condition analysis, and Crowd density.
     */
    suspend fun scoreDynamicRoutes(
        originLat: Double,
        originLng: Double,
        destinationQuery: String,
        destLatFallback: Double = 0.0,
        destLngFallback: Double = 0.0,
        transportMode: String = "driving"
    ): Result<List<RouteOption>> = withContext(Dispatchers.IO) {
        try {
            // STEP 1: Geocode destination query or use fallback coordinates
            val (destLat, destLng) = try {
                if (destinationQuery.isNotBlank()) {
                    geocodeDestination(destinationQuery)
                } else if (destLatFallback != 0.0 && destLngFallback != 0.0) {
                    Pair(destLatFallback, destLngFallback)
                } else {
                    Pair(13.1500, 80.2000) // Default Puzhal, Chennai coordinates if query empty
                }
            } catch (e: Exception) {
                if (destLatFallback != 0.0 && destLngFallback != 0.0) {
                    Pair(destLatFallback, destLngFallback)
                } else {
                    Pair(13.1500, 80.2000)
                }
            }

            // STEP 2: Handle missing/uninitialized or far-away origin coordinates
            val approxDistToDest = Math.hypot(destLat - originLat, destLng - originLng) * 111.0
            val (effectiveOriginLat, effectiveOriginLng) = if (originLat == 0.0 || originLng == 0.0) {
                // Infer origin ~4 km southwest of destination in the same local city region
                Pair(destLat - 0.035, destLng - 0.025)
            } else {
                Pair(originLat, originLng)
            }

            // STEP 3: Backend Route Analysis endpoint (FastAPI + NASA + OSRM)
            val backendResult = try {
                fetchBackendRoutes(effectiveOriginLat, effectiveOriginLng, destLat, destLng, destinationQuery, transportMode)
            } catch (e: Exception) {
                null
            }

            if (backendResult != null && backendResult.isNotEmpty()) {
                return@withContext Result.success(backendResult)
            }

            // STEP 4: Try OSRM (GoogleDirectionsRepository) — real, road-following alternatives.
            val googleResultRes = googleDirections.fetchRoutes(
                effectiveOriginLat, effectiveOriginLng, destLat, destLng, transportMode
            )
            
            val googleResult = googleResultRes.getOrNull()
            if (googleResultRes.isFailure) {
                android.util.Log.e("RouteScoringRepo", "Routing API Failed:", googleResultRes.exceptionOrNull())
            }

            if (googleResult != null && googleResult.isNotEmpty()) {
                return@withContext Result.success(googleResult)
            }

            // STEP 5: Fallback local route calculation engine (when both above are unavailable)
            val localRoutes = generateLocalFallbackRoutes(effectiveOriginLat, effectiveOriginLng, destLat, destLng, destinationQuery, transportMode)
            Result.success(localRoutes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchBackendRoutes(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        destinationQuery: String,
        transportMode: String = "driving"
    ): List<RouteOption> {
        val endpointUrl = "http://10.0.2.2:8000/analyze-route"
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 12000

        val requestJson = JSONObject().apply {
            put("source_lat", originLat)
            put("source_lon", originLng)
            put("destination_lat", destLat)
            put("destination_lon", destLng)
            put("mode", transportMode)
        }

        conn.outputStream.use { output ->
            output.write(requestJson.toString().toByteArray())
        }

        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Backend response error ${conn.responseCode}")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseText)
        if (json.getString("status") != "success") {
            throw Exception("Backend route analysis status failed")
        }

        val safestRouteObj = json.optJSONObject("safest_route")
        val safestRouteId = safestRouteObj?.optInt("route_id", 1) ?: 1
        val routesArray = json.getJSONArray("all_routes")
        val routes = mutableListOf<RouteOption>()

        for (i in 0 until routesArray.length()) {
            val route = routesArray.getJSONObject(i)
            val routeId = route.getInt("route_id")
            val coordinates = route.getJSONArray("coordinates")
            val points = mutableListOf<RoutePoint>()

            for (j in 0 until coordinates.length()) {
                val point = coordinates.getJSONObject(j)
                points.add(RoutePoint(lat = point.getDouble("latitude"), lng = point.getDouble("longitude")))
            }

            val lightingSafetyScore = route.optDouble("lighting_safety_score", 75.0)
            val averageLightScore = route.optDouble("average_light_score", 70.0)
            val lightingNorm = lightingSafetyScore / 100.0

            val crowdDensityStr = route.optString("crowd_density", "Unknown")
            val trafficCond = route.optString("traffic_condition", "Unknown Traffic")
            val trafficScore = route.optDouble("traffic_score", 0.0)
            val humanPresenceScore = route.optDouble("human_presence_score", 0.0)
            val activityDensityScore = route.optDouble("activity_density_score", 0.0)
            val pedestrianScore = route.optDouble("pedestrian_score", 0.0)
            val confidenceScore = route.optDouble("confidence_score", 1.0)
            val compositeScore = route.optDouble("composite_score", 0.0)

            val roundedScore = (Math.round(compositeScore * 100.0) / 100.0)

            val displayRisk = when {
                compositeScore >= 75.0 -> "Low Risk (Safest)"
                compositeScore >= 50.0 -> "Medium Risk"
                else -> "High Risk"
            }

            val viaRouteStr = route.optString("via_route", "via Alternative Route")
            val majorAreasStr = "Covers: Primary computed route path"

            val darkSpots = mutableListOf<RoutePoint>()
            if (routeId != safestRouteId && points.size > 2) {
                val midPoint = points[points.size / 2]
                darkSpots.add(midPoint)
            }

            routes.add(
                RouteOption(
                    routeId = "route_$routeId",
                    name = if (routeId == safestRouteId) "SafeHer Safest Recommended Route" else "Alternative Route $routeId",
                    viaRoute = viaRouteStr,
                    majorAreasCovered = majorAreasStr,
                    distance = "${route.getDouble("distance_km")} km",
                    duration = "${route.getDouble("duration_minutes").toInt()} mins",
                    compositeScore = roundedScore,
                    modelRiskLabel = if (compositeScore >= 75.0) "low" else if (compositeScore >= 50.0) "medium" else "high",
                    displayRisk = displayRisk,
                    lightingScore = averageLightScore,
                    crowdDensity = crowdDensityStr,
                    trafficCondition = trafficCond,
                    trafficScore = trafficScore,
                    humanPresenceScore = humanPresenceScore,
                    activityDensityScore = activityDensityScore,
                    pedestrianScore = pedestrianScore,
                    confidenceScore = confidenceScore,
                    darkSpots = darkSpots,
                    points = points
                )
            )
        }

        return routes.sortedByDescending { it.compositeScore }
    }

    private fun generateLocalFallbackRoutes(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        query: String,
        transportMode: String = "driving"
    ): List<RouteOption> {
        val dLat = destLat - originLat
        val dLng = destLng - originLng

        val locSeed = (((destLat * 1000).toInt() * 73856093) xor ((destLng * 1000).toInt() * 19349663) xor (query.hashCode()))
        val locVariance = (Math.abs(locSeed) % 1400) / 100.0 - 7.0

        val r1Score = (85.0 + locVariance).coerceIn(40.0, 95.0)
        val r2Score = (68.0 + locVariance * 0.8).coerceIn(35.0, 85.0)
        val r3Score = (48.0 + locVariance * 0.6).coerceIn(25.0, 70.0)

        fun formatModeDuration(distKm: Double, extraMins: Int = 0): String {
            val mins = when (transportMode.toLowerCase()) {
                "walking", "foot" -> (distKm * 12.5).toInt().coerceAtLeast(1) + extraMins
                "bicycling", "bike" -> (distKm * 3.75).toInt().coerceAtLeast(1) + extraMins
                else -> (distKm * 1.6).toInt().coerceAtLeast(1) + extraMins
            }
            return if (mins >= 60) {
                val h = mins / 60
                val m = mins % 60
                if (m == 0) "$h hr" else "$h hr $m mins"
            } else {
                "$mins mins"
            }
        }

        // 3 alternate route polylines
        val r1 = RouteOption(
            routeId = "route_1",
            name = "Via Main Arterial Road (High Lighting)",
            transportMode = transportMode,
            distance = "4.8 km",
            duration = formatModeDuration(4.8),
            compositeScore = r1Score,
            modelRiskLabel = "low",
            displayRisk = "Low Risk (Safest)",
            lightingScore = (r1Score * 0.88).coerceIn(20.0, 95.0),
            humanPresenceScore = (r1Score * 0.82).coerceIn(20.0, 92.0),
            activityDensityScore = (r1Score * 0.78).coerceIn(15.0, 88.0),
            trafficScore = (r1Score * 0.72).coerceIn(10.0, 85.0),
            pedestrianScore = (r1Score * 0.75).coerceIn(10.0, 88.0),
            confidenceScore = 0.91,
            crowdDensity = "HIGH",
            disclaimer = disclaimerText,
            points = listOf(
                RoutePoint(originLat, originLng),
                RoutePoint(originLat + dLat * 0.3, originLng + dLng * 0.2),
                RoutePoint(originLat + dLat * 0.7, originLng + dLng * 0.8),
                RoutePoint(destLat, destLng)
            )
        )

        val r2 = RouteOption(
            routeId = "route_2",
            name = "Via Central Park Avenue",
            transportMode = transportMode,
            distance = "5.5 km",
            duration = formatModeDuration(5.5, 2),
            compositeScore = r2Score,
            modelRiskLabel = "medium",
            displayRisk = "Medium Risk",
            lightingScore = (r2Score * 0.80).coerceIn(20.0, 85.0),
            humanPresenceScore = (r2Score * 0.75).coerceIn(15.0, 80.0),
            activityDensityScore = (r2Score * 0.70).coerceIn(15.0, 78.0),
            trafficScore = (r2Score * 0.65).coerceIn(10.0, 75.0),
            pedestrianScore = (r2Score * 0.70).coerceIn(10.0, 78.0),
            confidenceScore = 0.87,
            crowdDensity = "MEDIUM",
            disclaimer = disclaimerText,
            points = listOf(
                RoutePoint(originLat, originLng),
                RoutePoint(originLat + dLat * 0.25, originLng + dLng * 0.45),
                RoutePoint(originLat + dLat * 0.75, originLng + dLng * 0.55),
                RoutePoint(destLat, destLng)
            )
        )

        val route3Points = listOf(
            RoutePoint(originLat, originLng),
            RoutePoint(originLat + dLat * 0.2, originLng + dLng * 0.5),
            RoutePoint(originLat + dLat * 0.6, originLng + dLng * 0.6),
            RoutePoint(destLat, destLng)
        )

        val r3 = RouteOption(
            routeId = "route_3",
            name = "Via Service Bypass (Secondary Alley)",
            transportMode = transportMode,
            distance = "6.2 km",
            duration = formatModeDuration(6.2, 4),
            compositeScore = r3Score,
            modelRiskLabel = "high",
            displayRisk = "High Risk",
            lightingScore = (r3Score * 0.70).coerceIn(15.0, 70.0),
            humanPresenceScore = (r3Score * 0.60).coerceIn(10.0, 65.0),
            activityDensityScore = (r3Score * 0.55).coerceIn(10.0, 60.0),
            trafficScore = (r3Score * 0.60).coerceIn(10.0, 65.0),
            pedestrianScore = (r3Score * 0.55).coerceIn(10.0, 60.0),
            confidenceScore = 0.82,
            crowdDensity = "LOW (Isolated / Low Crowd)",
            trafficCondition = "Congested Traffic (Avg 9 km/h)",
            disclaimer = disclaimerText,
            darkSpots = listOf(route3Points[1], route3Points[2]),
            points = route3Points
        )

        return listOf(r1, r2, r3)
    }

    private fun geocodeDestination(query: String): Pair<Double, Double> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        
        // 1. Try local backend geocoder
        try {
            val endpointUrl = "http://10.0.2.2:8000/geocode?query=$encodedQuery"
            val url = URL(endpointUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                return Pair(json.getDouble("latitude"), json.getDouble("longitude"))
            }
        } catch (e: Exception) {
            // Ignore backend error and try direct Nominatim
        }

        // 2. Direct Nominatim OpenStreetMap fallback
        val url = URL("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=jsonv2&limit=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "SafeHer-App/1.0")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val array = org.json.JSONArray(responseText)
            if (array.length() > 0) {
                val obj = array.getJSONObject(0)
                return Pair(obj.getDouble("lat"), obj.getDouble("lon"))
            }
        }
        throw Exception("Destination search returned no results")
    }

    suspend fun saveSelectedJourney(
        userId: String,
        originLat: Double,
        originLng: Double,
        destinationAddress: String,
        destLat: Double,
        destLng: Double,
        route: RouteOption
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val journeyId = UUID.randomUUID().toString()
            val journey = Journey(
                journeyId = journeyId,
                userId = userId,
                originLat = originLat,
                originLng = originLng,
                destinationAddress = destinationAddress,
                destLat = destLat,
                destLng = destLng,
                selectedRouteId = route.routeId,
                selectedRouteName = route.name,
                routeScore = route.compositeScore,
                riskLabel = route.displayRisk,
                distance = route.distance,
                duration = route.duration,
                disclaimer = route.disclaimer,
                polylinePoints = route.points,
                status = "planned",
                createdAt = System.currentTimeMillis()
            )

            offlineSync.writeJourneyDocument(
                journeyId,
                mapOf(
                    "journeyId" to journey.journeyId,
                    "userId" to journey.userId,
                    "originLat" to journey.originLat,
                    "originLng" to journey.originLng,
                    "destinationAddress" to journey.destinationAddress,
                    "destLat" to journey.destLat,
                    "destLng" to journey.destLng,
                    "selectedRouteId" to journey.selectedRouteId,
                    "selectedRouteName" to journey.selectedRouteName,
                    "routeScore" to journey.routeScore,
                    "riskLabel" to journey.riskLabel,
                    "distance" to journey.distance,
                    "duration" to journey.duration,
                    "disclaimer" to journey.disclaimer,
                    "polylinePoints" to journey.polylinePoints.map { point -> mapOf("lat" to point.lat, "lng" to point.lng) },
                    "status" to journey.status,
                    "createdAt" to journey.createdAt
                )
            )

            Result.success(journeyId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startActiveJourney(
        journeyId: String,
        userId: String,
        context: android.content.Context
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf(
                "status" to "active",
                "startedAt" to System.currentTimeMillis()
            )
            offlineSync.writeJourneyUpdate(journeyId, updates)

            val intent = android.content.Intent(context, com.safeher.app.service.JourneyMonitoringService::class.java).apply {
                action = com.safeher.app.service.JourneyMonitoringService.ACTION_START_MONITORING
                putExtra(com.safeher.app.service.JourneyMonitoringService.EXTRA_JOURNEY_ID, journeyId)
                putExtra(com.safeher.app.service.JourneyMonitoringService.EXTRA_USER_ID, userId)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveDeviationAlert(journeyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf(
                "isDeviated" to false,
                "deviationAlertActive" to false
            )
            offlineSync.writeJourneyUpdate(journeyId, updates, "deviation_flag")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endJourney(
        journeyId: String,
        context: android.content.Context
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf(
                "status" to "completed",
                "endedAt" to System.currentTimeMillis()
            )
            offlineSync.writeJourneyUpdate(journeyId, updates)

            val intent = android.content.Intent(context, com.safeher.app.service.JourneyMonitoringService::class.java).apply {
                action = com.safeher.app.service.JourneyMonitoringService.ACTION_STOP_MONITORING
            }
            context.startService(intent)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun simulateDeviationAlert(journeyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf(
                "isDeviated" to true,
                "deviationAlertActive" to true
            )
            offlineSync.writeJourneyUpdate(journeyId, updates, "deviation_flag")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

