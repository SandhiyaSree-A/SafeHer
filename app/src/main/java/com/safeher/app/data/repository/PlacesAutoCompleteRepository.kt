package com.safeher.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
 * Real place search using the Photon API,
 * returning exact points instead of Nominatim's coarser area-level matches.
 */
class PlacesAutocompleteRepository {

    private val placesCache = mutableMapOf<String, PlaceDetails>()

    fun newSession() {
        // No longer needed for Photon, but kept for compatibility if used elsewhere
    }

    suspend fun autocomplete(
        query: String,
        biasLat: Double? = null,
        biasLng: Double? = null
    ): Result<List<PlaceSuggestion>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext Result.success(emptyList())

            val lat = biasLat ?: 13.0827
            val lon = biasLng ?: 80.2707

            val endpoint = "https://photon.komoot.io/api?q=${URLEncoder.encode(query, "UTF-8")}" +
                    "&lat=$lat&lon=$lon&limit=15&lang=en"

            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "SafeHer")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Photon API HTTP ${conn.responseCode}"))
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val features = json.optJSONArray("features")
            val results = mutableListOf<PlaceSuggestion>()
            
            if (features != null) {
                for (i in 0 until features.length()) {
                    val f = features.getJSONObject(i)
                    val properties = f.optJSONObject("properties") ?: continue
                    
                    if (properties.optString("countrycode") != "IN") continue
                    
                    val osmType = properties.optString("osm_type")
                    val osmId = properties.optLong("osm_id", -1L)
                    if (osmType.isEmpty() || osmId == -1L) continue
                    
                    val placeId = "$osmType$osmId"
                    
                    val name = properties.optString("name").takeIf { it.isNotBlank() }
                    val street = properties.optString("street").takeIf { it.isNotBlank() }
                    val district = properties.optString("district").takeIf { it.isNotBlank() }
                    val city = properties.optString("city").takeIf { it.isNotBlank() }
                    val state = properties.optString("state").takeIf { it.isNotBlank() }
                    
                    val primaryText = name ?: street ?: "Unknown Place"
                    val secondaryList = listOfNotNull(street, district, city, state)
                    val secondaryText = secondaryList.joinToString(", ")
                    
                    val geometry = f.optJSONObject("geometry")
                    val coordinates = geometry?.optJSONArray("coordinates")
                    if (coordinates != null && coordinates.length() >= 2) {
                        val lng = coordinates.getDouble(0)
                        val plat = coordinates.getDouble(1)
                        placesCache[placeId] = PlaceDetails(plat, lng, "$primaryText, $secondaryText".trim(',', ' '))
                    }

                    results.add(PlaceSuggestion(placeId, primaryText, secondaryText))
                    if (results.size >= 5) break // Limit to 5 after filtering
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaceDetails(placeId: String): Result<PlaceDetails> = withContext(Dispatchers.IO) {
        val details = placesCache[placeId]
        if (details != null) {
            Result.success(details)
        } else {
            Result.failure(Exception("Place details not found in cache for $placeId"))
        }
    }
}