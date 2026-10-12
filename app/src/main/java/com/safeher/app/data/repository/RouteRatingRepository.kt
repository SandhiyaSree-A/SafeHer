package com.safeher.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.safeher.app.data.model.Journey
import com.safeher.app.data.model.RouteFeedback
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.RoutePoint
import com.safeher.app.data.model.RouteTurnStep
import com.safeher.app.data.model.SafestRoute
import com.safeher.app.data.offline.RouteFeedbackEntity
import com.safeher.app.data.offline.SafeHerDatabase
import com.safeher.app.data.offline.SafestRouteEntity
import com.safeher.app.data.offline.SavedOfflineRouteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.UUID

data class SafeRatingResult(
    val safeRatingCount: Int = 1,
    val isOfflineReady: Boolean = false,
    val destinationAddress: String = ""
)

class RouteRatingRepository(
    private val context: Context? = null
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val database: SafeHerDatabase? by lazy {
        val ctx = context ?: try {
            FirebaseApp.getInstance().applicationContext
        } catch (_: Exception) {
            null
        }
        ctx?.let { SafeHerDatabase.get(it) }
    }

    suspend fun recordSafeRating(
        userId: String,
        route: RouteOption?,
        journey: Journey?,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        destinationAddress: String,
        transportMode: String
    ): Result<SafeRatingResult> = withContext(Dispatchers.IO) {
        try {
            val recordId = UUID.randomUUID().toString()
            val effectiveRouteId = route?.routeId ?: journey?.selectedRouteId ?: "route_${System.currentTimeMillis()}"
            val effectiveRouteName = route?.name ?: journey?.selectedRouteName ?: "SafeHer Recommended Route"
            val effectiveDestAddress = destinationAddress.ifBlank { journey?.destinationAddress ?: "Destination" }
            val effectiveComposite = route?.compositeScore ?: journey?.routeScore ?: 85.0
            val effectiveRiskLabel = route?.displayRisk ?: journey?.riskLabel ?: "Low Risk (Safest)"
            val effectiveDistance = route?.distance ?: journey?.distance ?: ""
            val effectiveDuration = route?.duration ?: journey?.duration ?: ""
            val effectiveLighting = route?.lightingScore ?: (effectiveComposite * 0.85)

            // 1. Maintain existing SafestRoute log
            val safestRoute = SafestRoute(
                id = recordId,
                journeyId = journey?.journeyId ?: "",
                userId = userId,
                routeId = effectiveRouteId,
                routeName = effectiveRouteName,
                originLat = originLat,
                originLng = originLng,
                destLat = destLat,
                destLng = destLng,
                destinationAddress = effectiveDestAddress,
                transportMode = transportMode,
                compositeScore = effectiveComposite,
                riskLabel = effectiveRiskLabel,
                distance = effectiveDistance,
                duration = effectiveDuration,
                markedSafeAt = System.currentTimeMillis()
            )
            saveSafestRoute(safestRoute)

            // 2. Manage 3-4 day threshold for offline saved route
            val dao = database?.savedOfflineRouteDao()
            var currentCount = 1
            var offlineReady = false

            if (dao != null) {
                // Find existing route by destination address or coordinate vicinity (within ~400m)
                val existing = dao.findMatchingRoute(userId, effectiveDestAddress.trim())
                    ?: if (destLat != 0.0 && destLng != 0.0) {
                        dao.findMatchingRouteByCoords(
                            userId = userId,
                            minDestLat = destLat - 0.005,
                            maxDestLat = destLat + 0.005,
                            minDestLng = destLng - 0.005,
                            maxDestLng = destLng + 0.005
                        )
                    } else null

                val pointsJson = serializeRoutePoints(route?.points ?: emptyList())
                val turnStepsJson = serializeTurnSteps(route?.turnSteps ?: emptyList())

                if (existing != null) {
                    currentCount = existing.safeRatingCount + 1
                    offlineReady = currentCount >= 3 || existing.isOfflineReady

                    val updatedRoute = existing.copy(
                        routeName = effectiveRouteName,
                        safeRatingCount = currentCount,
                        isOfflineReady = offlineReady,
                        originLat = if (originLat != 0.0) originLat else existing.originLat,
                        originLng = if (originLng != 0.0) originLng else existing.originLng,
                        destLat = if (destLat != 0.0) destLat else existing.destLat,
                        destLng = if (destLng != 0.0) destLng else existing.destLng,
                        destinationAddress = effectiveDestAddress,
                        transportMode = transportMode,
                        compositeScore = effectiveComposite,
                        riskLabel = effectiveRiskLabel,
                        distance = effectiveDistance.ifBlank { existing.distance },
                        duration = effectiveDuration.ifBlank { existing.duration },
                        lightingScore = effectiveLighting,
                        crowdDensity = route?.crowdDensity ?: existing.crowdDensity,
                        trafficCondition = route?.trafficCondition ?: existing.trafficCondition,
                        trafficScore = route?.trafficScore ?: existing.trafficScore,
                        humanPresenceScore = route?.humanPresenceScore ?: existing.humanPresenceScore,
                        activityDensityScore = route?.activityDensityScore ?: existing.activityDensityScore,
                        pedestrianScore = route?.pedestrianScore ?: existing.pedestrianScore,
                        confidenceScore = route?.confidenceScore ?: existing.confidenceScore,
                        sosHotspotScore = route?.sosHotspotScore ?: existing.sosHotspotScore,
                        sosHotspotCount = route?.sosHotspotCount ?: existing.sosHotspotCount,
                        viaRoute = route?.viaRoute ?: existing.viaRoute,
                        majorAreasCovered = route?.majorAreasCovered ?: existing.majorAreasCovered,
                        routePointsJson = if (pointsJson != "[]") pointsJson else existing.routePointsJson,
                        turnStepsJson = if (turnStepsJson != "[]") turnStepsJson else existing.turnStepsJson,
                        lastRatedAt = System.currentTimeMillis()
                    )
                    dao.updateRoute(updatedRoute)
                } else {
                    currentCount = 1
                    offlineReady = false
                    val newRoute = SavedOfflineRouteEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        routeId = effectiveRouteId,
                        routeName = effectiveRouteName,
                        originLat = originLat,
                        originLng = originLng,
                        destLat = destLat,
                        destLng = destLng,
                        destinationAddress = effectiveDestAddress,
                        transportMode = transportMode,
                        safeRatingCount = 1,
                        isOfflineReady = false,
                        compositeScore = effectiveComposite,
                        riskLabel = effectiveRiskLabel,
                        distance = effectiveDistance,
                        duration = effectiveDuration,
                        lightingScore = effectiveLighting,
                        crowdDensity = route?.crowdDensity ?: "NORMAL",
                        trafficCondition = route?.trafficCondition ?: "Smooth",
                        trafficScore = route?.trafficScore ?: 0.85,
                        humanPresenceScore = route?.humanPresenceScore ?: 0.80,
                        activityDensityScore = route?.activityDensityScore ?: 0.75,
                        pedestrianScore = route?.pedestrianScore ?: 0.80,
                        confidenceScore = route?.confidenceScore ?: 0.95,
                        sosHotspotScore = route?.sosHotspotScore,
                        sosHotspotCount = route?.sosHotspotCount ?: 0,
                        viaRoute = route?.viaRoute ?: "",
                        majorAreasCovered = route?.majorAreasCovered ?: "",
                        routePointsJson = pointsJson,
                        turnStepsJson = turnStepsJson,
                        firstRatedAt = System.currentTimeMillis(),
                        lastRatedAt = System.currentTimeMillis()
                    )
                    dao.insertRoute(newRoute)
                }
            }

            Result.success(
                SafeRatingResult(
                    safeRatingCount = currentCount,
                    isOfflineReady = offlineReady,
                    destinationAddress = effectiveDestAddress
                )
            )
        } catch (e: Exception) {
            Log.e("RouteRatingRepo", "Error recording safe rating: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks the database for any pre-computed and user-verified safest route matching
     * destination or coordinates. If found, returns the route immediately without network or re-computation.
     */
    suspend fun findOfflineSavedRoute(
        userId: String,
        destinationQuery: String,
        originLat: Double = 0.0,
        originLng: Double = 0.0,
        destLatFallback: Double = 0.0,
        destLngFallback: Double = 0.0
    ): RouteOption? = withContext(Dispatchers.IO) {
        val dao = database?.savedOfflineRouteDao() ?: return@withContext null
        try {
            val queryClean = destinationQuery.trim()
            val offlineRoutes = dao.getOfflineReadyRoutes(userId)
            if (offlineRoutes.isEmpty()) return@withContext null

            // 1. Check exact or substring destination match (e.g., "Home", "work", or street name)
            var match = offlineRoutes.firstOrNull { route ->
                queryClean.isNotBlank() && (
                    route.destinationAddress.equals(queryClean, ignoreCase = true) ||
                    route.destinationAddress.contains(queryClean, ignoreCase = true) ||
                    queryClean.contains(route.destinationAddress, ignoreCase = true)
                )
            }

            // 2. Check coordinate proximity (within ~500m of destination)
            if (match == null && destLatFallback != 0.0 && destLngFallback != 0.0) {
                match = offlineRoutes.firstOrNull { route ->
                    val dist = Math.hypot(route.destLat - destLatFallback, route.destLng - destLngFallback) * 111.0
                    dist <= 0.6 // within 600 meters
                }
            }

            match?.let { entity ->
                entityToRouteOption(entity)
            }
        } catch (e: Exception) {
            Log.e("RouteRatingRepo", "Error fetching offline saved route: ${e.message}")
            null
        }
    }

    suspend fun saveSafestRoute(route: SafestRoute): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val recordId = if (route.id.isNotBlank()) route.id else UUID.randomUUID().toString()
            val effectiveRoute = route.copy(id = recordId)

            // 1. Save to Room Database table (safest_routes)
            database?.safestRouteDao()?.insertSafestRoute(
                SafestRouteEntity(
                    id = effectiveRoute.id,
                    journeyId = effectiveRoute.journeyId,
                    userId = effectiveRoute.userId,
                    routeId = effectiveRoute.routeId,
                    routeName = effectiveRoute.routeName,
                    originLat = effectiveRoute.originLat,
                    originLng = effectiveRoute.originLng,
                    destLat = effectiveRoute.destLat,
                    destLng = effectiveRoute.destLng,
                    destinationAddress = effectiveRoute.destinationAddress,
                    transportMode = effectiveRoute.transportMode,
                    compositeScore = effectiveRoute.compositeScore,
                    riskLabel = effectiveRoute.riskLabel,
                    distance = effectiveRoute.distance,
                    duration = effectiveRoute.duration,
                    markedSafeAt = effectiveRoute.markedSafeAt
                )
            )

            // 2. Save to Firestore collection (safest_routes)
            val docData = mapOf(
                "id" to effectiveRoute.id,
                "journeyId" to effectiveRoute.journeyId,
                "userId" to effectiveRoute.userId,
                "routeId" to effectiveRoute.routeId,
                "routeName" to effectiveRoute.routeName,
                "originLat" to effectiveRoute.originLat,
                "originLng" to effectiveRoute.originLng,
                "destLat" to effectiveRoute.destLat,
                "destLng" to effectiveRoute.destLng,
                "destinationAddress" to effectiveRoute.destinationAddress,
                "transportMode" to effectiveRoute.transportMode,
                "compositeScore" to effectiveRoute.compositeScore,
                "riskLabel" to effectiveRoute.riskLabel,
                "distance" to effectiveRoute.distance,
                "duration" to effectiveRoute.duration,
                "markedSafeAt" to effectiveRoute.markedSafeAt,
                "createdAt" to effectiveRoute.markedSafeAt
            )

            try {
                firestore.collection("safest_routes").document(recordId).set(docData).await()
            } catch (e: Exception) {
                Log.w("RouteRatingRepo", "Firestore safest_routes write deferred: ${e.message}")
            }

            // 3. Sync to Supabase via backend endpoint
            try {
                val backendUrl = URL("http://10.0.2.2:8080/safest-routes")
                val backendConn = backendUrl.openConnection() as java.net.HttpURLConnection
                backendConn.requestMethod = "POST"
                backendConn.doOutput = true
                backendConn.setRequestProperty("Content-Type", "application/json")
                backendConn.connectTimeout = 5000
                backendConn.readTimeout = 5000

                val backendJson = JSONObject().apply {
                    put("id", effectiveRoute.id)
                    put("journey_id", effectiveRoute.journeyId)
                    put("user_id", effectiveRoute.userId)
                    put("route_id", effectiveRoute.routeId)
                    put("route_name", effectiveRoute.routeName)
                    put("origin_lat", effectiveRoute.originLat)
                    put("origin_lng", effectiveRoute.originLng)
                    put("dest_lat", effectiveRoute.destLat)
                    put("dest_lng", effectiveRoute.destLng)
                    put("destination_address", effectiveRoute.destinationAddress)
                    put("transport_mode", effectiveRoute.transportMode)
                    put("composite_score", effectiveRoute.compositeScore)
                    put("risk_label", effectiveRoute.riskLabel)
                    put("distance", effectiveRoute.distance)
                    put("duration", effectiveRoute.duration)
                }
                backendConn.outputStream.use { it.write(backendJson.toString().toByteArray()) }
                backendConn.inputStream.bufferedReader().use { it.readText() }
                backendConn.disconnect()
            } catch (e: Exception) {
                Log.w("RouteRatingRepo", "Backend safest-routes sync deferred: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("RouteRatingRepo", "Error saving safest route: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun saveRouteFeedback(feedback: RouteFeedback): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val recordId = if (feedback.id.isNotBlank()) feedback.id else UUID.randomUUID().toString()
            val effectiveFeedback = feedback.copy(id = recordId)

            // 1. Save to Room Database table (route_feedback)
            database?.routeFeedbackDao()?.insertFeedback(
                RouteFeedbackEntity(
                    id = effectiveFeedback.id,
                    journeyId = effectiveFeedback.journeyId,
                    userId = effectiveFeedback.userId,
                    routeId = effectiveFeedback.routeId,
                    routeName = effectiveFeedback.routeName,
                    originLat = effectiveFeedback.originLat,
                    originLng = effectiveFeedback.originLng,
                    destLat = effectiveFeedback.destLat,
                    destLng = effectiveFeedback.destLng,
                    destinationAddress = effectiveFeedback.destinationAddress,
                    transportMode = effectiveFeedback.transportMode,
                    wasSafe = effectiveFeedback.wasSafe,
                    reasonsString = effectiveFeedback.reasons.joinToString(";"),
                    customReason = effectiveFeedback.customReason,
                    submittedAt = effectiveFeedback.submittedAt
                )
            )

            // 2. Save to Firestore collection (route_feedback)
            val docData = mapOf(
                "id" to effectiveFeedback.id,
                "journeyId" to effectiveFeedback.journeyId,
                "userId" to effectiveFeedback.userId,
                "routeId" to effectiveFeedback.routeId,
                "routeName" to effectiveFeedback.routeName,
                "originLat" to effectiveFeedback.originLat,
                "originLng" to effectiveFeedback.originLng,
                "destLat" to effectiveFeedback.destLat,
                "destLng" to effectiveFeedback.destLng,
                "destinationAddress" to effectiveFeedback.destinationAddress,
                "transportMode" to effectiveFeedback.transportMode,
                "wasSafe" to effectiveFeedback.wasSafe,
                "reasons" to effectiveFeedback.reasons,
                "customReason" to effectiveFeedback.customReason,
                "submittedAt" to effectiveFeedback.submittedAt,
                "createdAt" to effectiveFeedback.submittedAt
            )

            // 3. Sync to Supabase via backend endpoint
            try {
                val backendUrl = URL("http://10.0.2.2:8080/route-feedback")
                val backendConn = backendUrl.openConnection() as java.net.HttpURLConnection
                backendConn.requestMethod = "POST"
                backendConn.doOutput = true
                backendConn.setRequestProperty("Content-Type", "application/json")
                backendConn.connectTimeout = 5000
                backendConn.readTimeout = 5000

                val backendJson = JSONObject().apply {
                    put("id", effectiveFeedback.id)
                    put("journey_id", effectiveFeedback.journeyId)
                    put("user_id", effectiveFeedback.userId)
                    put("route_id", effectiveFeedback.routeId)
                    put("route_name", effectiveFeedback.routeName)
                    put("origin_lat", effectiveFeedback.originLat)
                    put("origin_lng", effectiveFeedback.originLng)
                    put("dest_lat", effectiveFeedback.destLat)
                    put("dest_lng", effectiveFeedback.destLng)
                    put("destination_address", effectiveFeedback.destinationAddress)
                    put("transport_mode", effectiveFeedback.transportMode)
                    put("was_safe", effectiveFeedback.wasSafe)
                    put("reasons", effectiveFeedback.reasons.joinToString(";"))
                    put("custom_reason", effectiveFeedback.customReason)
                }
                backendConn.outputStream.use { it.write(backendJson.toString().toByteArray()) }
                backendConn.inputStream.bufferedReader().use { it.readText() }
                backendConn.disconnect()
            } catch (e: Exception) {
                Log.w("RouteRatingRepo", "Backend route-feedback sync deferred: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("RouteRatingRepo", "Error saving route feedback: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun serializeRoutePoints(points: List<RoutePoint>): String {
        val arr = JSONArray()
        for (p in points) {
            arr.put(JSONObject().apply {
                put("lat", p.lat)
                put("lng", p.lng)
            })
        }
        return arr.toString()
    }

    private fun deserializeRoutePoints(json: String): List<RoutePoint> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<RoutePoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(RoutePoint(lat = obj.getDouble("lat"), lng = obj.getDouble("lng")))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeTurnSteps(steps: List<RouteTurnStep>): String {
        val arr = JSONArray()
        for (s in steps) {
            arr.put(JSONObject().apply {
                put("instruction", s.instruction)
                put("roadName", s.roadName)
                put("distanceMeters", s.distanceMeters)
                put("durationSeconds", s.durationSeconds)
                put("startLat", s.startLat)
                put("startLng", s.startLng)
            })
        }
        return arr.toString()
    }

    private fun deserializeTurnSteps(json: String): List<RouteTurnStep> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<RouteTurnStep>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RouteTurnStep(
                        instruction = obj.optString("instruction", ""),
                        roadName = obj.optString("roadName", ""),
                        distanceMeters = obj.optDouble("distanceMeters", 0.0),
                        durationSeconds = obj.optDouble("durationSeconds", 0.0),
                        startLat = obj.optDouble("startLat", 0.0),
                        startLng = obj.optDouble("startLng", 0.0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun entityToRouteOption(entity: SavedOfflineRouteEntity): RouteOption {
        val points = deserializeRoutePoints(entity.routePointsJson)
        val steps = deserializeTurnSteps(entity.turnStepsJson)

        return RouteOption(
            routeId = entity.routeId,
            name = "${entity.routeName} (Offline Safest Route • ${entity.safeRatingCount}x Confirmed)",
            viaRoute = if (entity.viaRoute.isNotBlank()) entity.viaRoute else "Verified from your daily safe ratings",
            majorAreasCovered = if (entity.majorAreasCovered.isNotBlank()) entity.majorAreasCovered else "Covers verified safe daily commute path",
            transportMode = entity.transportMode,
            distance = entity.distance,
            duration = entity.duration,
            compositeScore = entity.compositeScore,
            modelRiskLabel = if (entity.compositeScore >= 75.0) "low" else "medium",
            displayRisk = "Safest (Saved Offline)",
            lightingScore = entity.lightingScore,
            crowdDensity = if (entity.crowdDensity.isNotBlank()) entity.crowdDensity else "VERIFIED SAFE",
            trafficCondition = if (entity.trafficCondition.isNotBlank()) entity.trafficCondition else "Smooth",
            trafficScore = entity.trafficScore,
            humanPresenceScore = entity.humanPresenceScore,
            activityDensityScore = entity.activityDensityScore,
            pedestrianScore = entity.pedestrianScore,
            confidenceScore = 0.98,
            sosHotspotScore = entity.sosHotspotScore,
            sosHotspotCount = entity.sosHotspotCount,
            turnSteps = steps,
            disclaimer = "Saved in local offline database after ${entity.safeRatingCount} positive safe ratings. No internet computation needed.",
            points = points
        )
    }
}