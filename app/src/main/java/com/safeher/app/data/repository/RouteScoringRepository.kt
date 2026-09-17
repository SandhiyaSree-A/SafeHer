package com.safeher.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseApp
import com.safeher.app.data.model.Journey
import com.safeher.app.data.offline.OfflineSyncRepository
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RouteScoringRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val offlineSync = OfflineSyncRepository.get(FirebaseApp.getInstance().applicationContext)
    private val disclaimerText = "Risk-awareness estimate for prototype/demo purposes only, not a guarantee of real-world safety or crime prediction"

    /**
     * Calls scoreRoute endpoint (or falls back to built-in fallback model engine)
     */
    suspend fun scoreRoutes(
        originLat: Double,
        originLng: Double,
        destinationQuery: String
): Result<List<RouteOption>> =
    withContext(Dispatchers.IO) {

        try {

            // STEP 1: Convert destination name to coordinates
            val destination =
                geocodeDestination(destinationQuery)

            val destLat = destination.first
            val destLng = destination.second

            // Android Emulator → PC localhost
            val endpointUrl =
                "http://10.0.2.2:8000/analyze-route"

            val url = URL(endpointUrl)

            val conn =
                url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"

            conn.doOutput = true

            conn.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            conn.connectTimeout = 15000
            conn.readTimeout = 60000

            val requestJson = JSONObject().apply {

                put("source_lat", originLat)

                put("source_lon", originLng)

                put("destination_lat", destLat)

                put("destination_lon", destLng)
            }

            conn.outputStream.use { output ->

                output.write(
                    requestJson
                        .toString()
                        .toByteArray()
                )
            }

            val responseCode = conn.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK) {

                val errorText =
                    conn.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }

                throw Exception(
                    "Backend error $responseCode: $errorText"
                )
            }

            val responseText =
                conn.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            val json = JSONObject(responseText)

            if (json.getString("status") != "success") {
                throw Exception("Route analysis failed")
            }

            val safestRoute =
                json.getJSONObject("safest_route")

            val safestRouteId =
                safestRoute.getInt("route_id")

            val routesArray =
                json.getJSONArray("all_routes")

            val routes =
                mutableListOf<RouteOption>()

            for (i in 0 until routesArray.length()) {

                val route =
                    routesArray.getJSONObject(i)

                val routeId =
                    route.getInt("route_id")

                val coordinates =
                    route.getJSONArray("coordinates")

                val points =
                    mutableListOf<RoutePoint>()

                for (j in 0 until coordinates.length()) {

                    val point =
                        coordinates.getJSONObject(j)

                    points.add(

                        RoutePoint(
                            lat = point.getDouble("latitude"),
                            lng = point.getDouble("longitude")
                        )
                    )
                }

                val lightingSafetyScore =
                    route.getDouble("lighting_safety_score")

                val averageLightScore =
                    route.getDouble("average_light_score")

                val compositeScore =
                    lightingSafetyScore / 100.0

                val displayRisk =
                    when {

                        lightingSafetyScore >= 75 ->
                            "Low Risk (Safest)"

                        lightingSafetyScore >= 50 ->
                            "Medium Risk"

                        else ->
                            "High Risk"
                    }

                routes.add(

                    RouteOption(

                        routeId = "route_$routeId",

                        name =
                            if (routeId == safestRouteId)
                                "SafeHer Recommended Route"
                            else
                                "Alternative Route $routeId",

                        distance =
                            "${route.getDouble("distance_km")} km",

                        duration =
                            "${route.getDouble("duration_minutes").toInt()} mins",

                        compositeScore = compositeScore,

                        modelRiskLabel =
                            when {

                                lightingSafetyScore >= 75 -> "low"

                                lightingSafetyScore >= 50 -> "medium"

                                else -> "high"
                            },

                        displayRisk = displayRisk,

                        lightingScore =
                            averageLightScore / 100.0,

                        crowdDensity = "unknown",

                        points = points
                    )
                )
            }

            Result.success(
                routes.sortedByDescending {
                    it.compositeScore
                }
            )

        } catch (e: Exception) {

            Result.failure(e)
        }
    }

    /**
     * Local resilient scoring fallback matching Part A model logic
     */
    
    }

    /**
     * Stores selected journey doc in Firestore collection `journeys/{journeyId}`
     */
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

    /**
     * Starts journey: updates status to "active", startedAt timestamp, and starts JourneyMonitoringService
     */
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

            // Start foreground service
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

    /**
     * Resets deviation alert state when user clicks "I'm fine, continue"
     */
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

    /**
     * Ends active journey: updates status to "completed" and stops service
     */
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

            // Stop foreground service
            val intent = android.content.Intent(context, com.safeher.app.service.JourneyMonitoringService::class.java).apply {
                action = com.safeher.app.service.JourneyMonitoringService.ACTION_STOP_MONITORING
            }
            context.startService(intent)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Simulation helper: instantly forces off-path deviation alert flag for testing
     */
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
    private fun geocodeDestination(query: String): Pair<Double, Double> {

        val encodedQuery =
            java.net.URLEncoder.encode(query, "UTF-8")

        val endpointUrl =
            "http://10.0.2.2:8000/geocode?query=$encodedQuery"

        val url = URL(endpointUrl)

        val conn =
            url.openConnection() as HttpURLConnection

        conn.requestMethod = "GET"

        conn.connectTimeout = 10000
        conn.readTimeout = 15000

        val responseCode = conn.responseCode

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Destination not found")
        }

        val responseText =
            conn.inputStream
                .bufferedReader()
                .use { it.readText() }

        val json = JSONObject(responseText)

        val latitude =
            json.getDouble("latitude")

        val longitude =
            json.getDouble("longitude")

        return Pair(latitude, longitude)
    }
}
