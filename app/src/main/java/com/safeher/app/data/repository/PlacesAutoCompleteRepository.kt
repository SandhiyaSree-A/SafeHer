package com.safeher.app.data.repository

import com.safeher.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

data class PlaceDetails(
    val lat: Double,
    val lng: Double,
    val formattedAddress: String
)

/**
 * Real place search using the Google Places API (Autocomplete + Place Details),
 * returning exact points instead of Nominatim's coarser area-level matches.
 */
class PlacesAutocompleteRepository {

    // Groups requests from one typing session together for accurate Places billing.
    private var sessionToken: String = UUID.randomUUID().toString()

    fun newSession() {
        sessionToken = UUID.randomUUID().toString()
    }

    suspend fun autocomplete(
        query: String,
        biasLat: Double? = null,
        biasLng: Double? = null
    ): Result<List<PlaceSuggestion>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.MAPS_API_KEY
            if (query.isBlank()) return@withContext Result.success(emptyList())

            val locationBias = if (biasLat != null && biasLng != null) {
                "&locationbias=circle:50000@$biasLat,$biasLng" // 50km bias radius
            } else ""

            val endpoint = "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
                    "?input=${URLEncoder.encode(query, "UTF-8")}" +
                    "&sessiontoken=$sessionToken" +
                    locationBias +
                    "&key=${URLEncoder.encode(apiKey, "UTF-8")}"

            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Places Autocomplete HTTP ${conn.responseCode}"))
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val status = json.optString("status")
            if (status != "OK" && status != "ZERO_RESULTS") {
                return@withContext Result.failure(
                    Exception("Places Autocomplete status: $status (${json.optString("error_message")})")
                )
            }

            val predictions = json.optJSONArray("predictions")
            val results = mutableListOf<PlaceSuggestion>()
            if (predictions != null) {
                for (i in 0 until predictions.length()) {
                    val p = predictions.getJSONObject(i)
                    val structured = p.optJSONObject("structured_formatting")
                    results.add(
                        PlaceSuggestion(
                            placeId = p.getString("place_id"),
                            primaryText = structured?.optString("main_text") ?: p.optString("description"),
                            secondaryText = structured?.optString("secondary_text") ?: ""
                        )
                    )
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaceDetails(placeId: String): Result<PlaceDetails> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.MAPS_API_KEY
            val endpoint = "https://maps.googleapis.com/maps/api/place/details/json" +
                    "?place_id=${URLEncoder.encode(placeId, "UTF-8")}" +
                    "&fields=geometry,formatted_address" +
                    "&sessiontoken=$sessionToken" +
                    "&key=${URLEncoder.encode(apiKey, "UTF-8")}"

            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Place Details HTTP ${conn.responseCode}"))
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            if (json.optString("status") != "OK") {
                return@withContext Result.failure(Exception("Place Details status: ${json.optString("status")}"))
            }

            val result = json.getJSONObject("result")
            val location = result.getJSONObject("geometry").getJSONObject("location")

            // A fresh session token should be used for the next autocomplete session
            // (Google's billing model groups one search session as a single unit).
            sessionToken = UUID.randomUUID().toString()

            Result.success(
                PlaceDetails(
                    lat = location.getDouble("lat"),
                    lng = location.getDouble("lng"),
                    formattedAddress = result.optString("formatted_address")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}