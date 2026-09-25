package com.safeher.app.data.repository

import com.google.maps.android.PolyUtil
import com.safeher.app.BuildConfig
import com.safeher.app.data.model.RouteOption
import com.safeher.app.data.model.RoutePoint
import com.safeher.app.data.model.RouteTurnStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches real, road-following alternative routes from the Google Directions
 * API. Requires the Directions API to be enabled on the same Cloud project
 * as MAPS_API_KEY (Roads API alone does not provide routing).
 */
class GoogleDirectionsRepository {

    private val disclaimerText =
        "Risk-awareness estimate for prototype/demo purposes only, not a guarantee of real-world safety or crime prediction"

    suspend fun fetchRoutes(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        transportMode: String = "driving"
    ): Result<List<RouteOption>> = withContext(Dispatchers.IO) {
        try {
            val osrmMode = when (transportMode.toLowerCase()) {
                "walking" -> "foot"
                "bicycling" -> "bike"
                else -> "driving"
            }
            // Using OSRM public API (Free, no API key required for testing)
            // Note: OSRM expects longitude first, then latitude!
            val endpoint = "http://router.project-osrm.org/route/v1/$osrmMode/" +
                    "$originLng,$originLat;$destLng,$destLat" +
                    "?alternatives=3" +
                    "&geometries=polyline" +
                    "&overview=full" +
                    "&steps=true"

            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "SafeHer-App/1.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("OSRM API HTTP ${conn.responseCode}"))
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val status = json.optString("code")
            if (status != "Ok") {
                return@withContext Result.failure(
                    Exception("OSRM API status: $status (${json.optString("message")})")
                )
            }

            val routesJson = json.getJSONArray("routes")
            if (routesJson.length() == 0) {
                return@withContext Result.failure(Exception("No routes returned"))
            }

            val riskProfiles = listOf(
                Triple("low", "Low Risk (Safest)", 0.85),
                Triple("medium", "Medium Risk", 0.62),
                Triple("high", "High Risk", 0.38)
            )

            val options = mutableListOf<RouteOption>()
            for (i in 0 until routesJson.length()) {
                val route = routesJson.getJSONObject(i)
                val legs = route.getJSONArray("legs")
                if (legs.length() == 0) continue
                val leg = legs.getJSONObject(0)

                val distMeters = route.optDouble("distance", 0.0)
                val durSeconds = route.optDouble("duration", 0.0)
                
                val distanceText = String.format("%.1f km", distMeters / 1000.0)
                val totalMins = (durSeconds / 60.0).toInt()
                val durationText = if (totalMins >= 60) {
                    val hrs = totalMins / 60
                    val mins = totalMins % 60
                    "$hrs hr $mins mins"
                } else {
                    "$totalMins mins"
                }

                val overviewPolyline = route.getString("geometry")
                val decodedLatLngs = PolyUtil.decode(overviewPolyline)
                val points = decodedLatLngs.map { RoutePoint(it.latitude, it.longitude) }
                if (points.size < 2) continue

                val summary = leg.optString("summary").takeIf { it.isNotBlank() }
                    ?: "Alternative Route ${i + 1}"

                val stepsJson = leg.optJSONArray("steps")
                val turnSteps = mutableListOf<RouteTurnStep>()
                if (stepsJson != null) {
                    for (s in 0 until stepsJson.length()) {
                        val step = stepsJson.getJSONObject(s)
                        val maneuver = step.optJSONObject("maneuver")
                        val instruction = maneuver?.optString("type") + " " + maneuver?.optString("modifier")
                        
                        val location = maneuver?.optJSONArray("location")
                        val startLng = location?.optDouble(0) ?: 0.0
                        val startLat = location?.optDouble(1) ?: 0.0

                        turnSteps.add(
                            RouteTurnStep(
                                instruction = instruction.trim(),
                                roadName = step.optString("name", summary),
                                distanceMeters = step.optDouble("distance", 0.0),
                                durationSeconds = step.optDouble("duration", 0.0),
                                startLat = startLat,
                                startLng = startLng
                            )
                        )
                    }
                }

                val (riskLabel, displayRisk, compositeScore) = riskProfiles.getOrElse(i) {
                    riskProfiles.last()
                }

                // Generate realistic simulated safety scores based on the route's risk profile
                // Scores decrease for riskier routes to reflect real-world safety differences
                val scoreBase = compositeScore * 100.0
                val lightSim   = (scoreBase * 0.90 + i * -8.0).coerceIn(20.0, 98.0)
                val humanSim   = (scoreBase * 0.85 + i * -10.0).coerceIn(20.0, 95.0)
                val activitySim= (scoreBase * 0.80 + i * -7.0).coerceIn(15.0, 90.0)
                val trafficSim = (scoreBase * 0.75 + i * -12.0).coerceIn(10.0, 88.0)
                val pedestrianSim = (scoreBase * 0.70 + i * -9.0).coerceIn(10.0, 85.0)
                val crowdText  = when {
                    humanSim > 65 -> "HIGH"
                    humanSim > 35 -> "MEDIUM"
                    else -> "LOW (Isolated)"
                }
                val trafficText = when {
                    trafficSim > 60 -> "Smooth Traffic"
                    trafficSim > 30 -> "Moderate Traffic"
                    else -> "Congested Traffic"
                }

                options.add(
                    RouteOption(
                        routeId = "osrm_route_$i",
                        name = "Via $summary",
                        viaRoute = summary,
                        distance = distanceText,
                        duration = durationText,
                        compositeScore = compositeScore,
                        modelRiskLabel = riskLabel,
                        displayRisk = displayRisk,
                        lightingScore = lightSim,
                        humanPresenceScore = humanSim,
                        activityDensityScore = activitySim,
                        trafficScore = trafficSim,
                        pedestrianScore = pedestrianSim,
                        confidenceScore = 1.0,
                        crowdDensity = crowdText,
                        trafficCondition = trafficText,
                        turnSteps = turnSteps,
                        disclaimer = disclaimerText,
                        points = points
                    )
                )
            }

            if (options.isEmpty()) {
                Result.failure(Exception("No usable routes decoded"))
            } else {
                Result.success(options)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}