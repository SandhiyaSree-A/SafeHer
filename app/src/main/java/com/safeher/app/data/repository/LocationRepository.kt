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
}
