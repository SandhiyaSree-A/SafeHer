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
        destLng: Double
    ): Result<List<RouteOption>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.MAPS_API_KEY
            if (apiKey.isBlank() || apiKey == "YOUR_GOOGLE_MAPS_API_KEY_HERE") {
                return@withContext Result.failure(Exception("MAPS_API_KEY is not set in local.properties"))
            }

            val endpoint = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=$originLat,$originLng" +
                    "&destination=$destLat,$destLng" +
                    "&alternatives=true" +
                    "&mode=driving" +
                    "&key=${URLEncoder.encode(apiKey, "UTF-8")}"

            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Directions API HTTP ${conn.responseCode}"))
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val status = json.optString("status")
            if (status != "OK") {
                return@withContext Result.failure(
                    Exception("Directions API status: $status (${json.optString("error_message")})")
                )
            }

            val routesJson = json.getJSONArray("routes")
            if (routesJson.length() == 0) {
                return@withContext Result.failure(Exception("No routes returned"))
            }

            // Deterministic risk styling per alternative slot (fastest = safest-styled,
            // matching the app's existing low/medium/high convention). These are display
            // labels, not a real safety model — Directions API has no safety signal.
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

                val distanceText = leg.getJSONObject("distance").getString("text")
                val durationText = leg.getJSONObject("duration").getString("text")

                val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")
                val decodedLatLngs = PolyUtil.decode(overviewPolyline)
                val points = decodedLatLngs.map { RoutePoint(it.latitude, it.longitude) }
                if (points.size < 2) continue

                val summary = route.optString("summary").takeIf { it.isNotBlank() }
                    ?: "Route ${i + 1}"

                val stepsJson = leg.optJSONArray("steps")
                val turnSteps = mutableListOf<RouteTurnStep>()
                if (stepsJson != null) {
                    for (s in 0 until stepsJson.length()) {
                        val step = stepsJson.getJSONObject(s)
                        val instruction = step.optString("html_instructions")
                            .replace(Regex("<[^>]*>"), "") // strip Google's inline HTML tags
                        val startLoc = step.optJSONObject("start_location")
                        turnSteps.add(
                            RouteTurnStep(
                                instruction = instruction,
                                roadName = summary,
                                distanceMeters = step.optJSONObject("distance")?.optDouble("value") ?: 0.0,
                                durationSeconds = step.optJSONObject("duration")?.optDouble("value") ?: 0.0,
                                startLat = startLoc?.optDouble("lat") ?: 0.0,
                                startLng = startLoc?.optDouble("lng") ?: 0.0
                            )
                        )
                    }
                }

                val (riskLabel, displayRisk, compositeScore) = riskProfiles.getOrElse(i) {
                    riskProfiles.last()
                }

                options.add(
                    RouteOption(
                        routeId = "google_route_$i",
                        name = "Via $summary",
                        viaRoute = summary,
                        distance = distanceText,
                        duration = durationText,
                        compositeScore = compositeScore,
                        modelRiskLabel = riskLabel,
                        displayRisk = displayRisk,
                        lightingScore = compositeScore, // no real lighting signal from Directions; mirrors compositeScore
                        crowdDensity = "unknown",
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