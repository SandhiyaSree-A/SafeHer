package com.safeher.app.util

import android.content.Context
import com.safeher.app.data.model.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists a journey's route points to internal storage so deviation detection
 * works offline (no Firestore round-trip required).
 *
 * File layout: filesDir/route_cache/<journeyId>.json
 */
object RouteCache {

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "route_cache").also { it.mkdirs() }

    private fun cacheFile(context: Context, journeyId: String): File =
        File(cacheDir(context), "$journeyId.json")

    /** Serialize [points] to JSON and write to disk. Call from IO dispatcher. */
    suspend fun save(context: Context, journeyId: String, points: List<RoutePoint>) =
        withContext(Dispatchers.IO) {
            val array = JSONArray()
            points.forEach { p ->
                array.put(JSONObject().apply {
                    put("lat", p.lat)
                    put("lng", p.lng)
                })
            }
            cacheFile(context, journeyId).writeText(array.toString())
        }

    /** Load previously saved points. Returns empty list if nothing cached. */
    fun load(context: Context, journeyId: String): List<RoutePoint> {
        val file = cacheFile(context, journeyId)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RoutePoint(lat = obj.getDouble("lat"), lng = obj.getDouble("lng"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Delete the cached route for a given journey (e.g. after journey ends). */
    fun clear(context: Context, journeyId: String) {
        cacheFile(context, journeyId).delete()
    }
}
