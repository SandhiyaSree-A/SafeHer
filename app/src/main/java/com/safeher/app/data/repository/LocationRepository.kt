package com.safeher.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.safeher.app.data.offline.OfflineSyncRepository
import com.safeher.app.data.model.LocationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("MissingPermission")
suspend fun getFreshLocation(fusedLocationClient: FusedLocationProviderClient): LocationData? {
    return try {
        val loc = withTimeoutOrNull(8_000L) {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token
            ).await()
        } ?: fusedLocationClient.lastLocation.await()
        loc?.let { LocationData(lat = it.latitude, lng = it.longitude, updatedAt = System.currentTimeMillis()) }
    } catch (e: Exception) { null }
}

class LocationRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 10000L): Flow<LocationData> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(5000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val data = LocationData(
                        lat = location.latitude,
                        lng = location.longitude,
                        updatedAt = System.currentTimeMillis()
                    )
                    trySend(data)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    fun updateFirestoreLocation(
        uid: String,
        locationData: LocationData,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (uid.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                OfflineSyncRepository.get(context).writeLocationPing(
                    uid = uid,
                    journeyId = null,
                    lat = locationData.lat,
                    lng = locationData.lng,
                    timestamp = locationData.updatedAt
                )
                onSuccess()
            } catch (error: Exception) {
                onFailure(error)
            }
        }
    }

    /** Returns the best available current location, or null if unavailable. */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData? = getFreshLocation()

    /** Alias used by HomeViewModel – delegates to getCurrentLocation(). */
    @SuppressLint("MissingPermission")
    suspend fun getFreshLocation(): LocationData? {
        return try {
            val loc = withTimeoutOrNull(8_000L) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token
                ).await()
            } ?: fusedLocationClient.lastLocation.await()
            loc?.let { LocationData(lat = it.latitude, lng = it.longitude, updatedAt = System.currentTimeMillis()) }
        } catch (e: Exception) { null }
    }
}
