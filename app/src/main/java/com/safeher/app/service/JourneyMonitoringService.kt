package com.safeher.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import com.safeher.app.MainActivity
import com.safeher.app.data.model.Journey
import com.safeher.app.data.offline.OfflineSyncRepository
import com.safeher.app.data.model.RoutePoint
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class JourneyMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val firestore = FirebaseFirestore.getInstance()
    private val offlineSync by lazy { OfflineSyncRepository.get(this) }

    private var currentJourneyId: String? = null
    private var currentUserId: String? = null
    private var routePoints: List<LatLng> = emptyList()

    private var offPathStartTimestamp: Long? = null
    private var isDeviationAlertTriggered: Boolean = false

    companion object {
        const val ACTION_START_MONITORING = "com.safeher.app.action.START_JOURNEY_MONITORING"
        const val ACTION_STOP_MONITORING = "com.safeher.app.action.STOP_JOURNEY_MONITORING"
        const val EXTRA_JOURNEY_ID = "extra_journey_id"
        const val EXTRA_USER_ID = "extra_user_id"

        const val CHANNEL_ID = "journey_monitoring_channel"
        const val ALERT_CHANNEL_ID = "journey_deviation_alert_channel"
        const val NOTIFICATION_ID = 2001
        const val DEVIATION_NOTIFICATION_ID = 2002

        // Continuous off-path threshold: 2 minutes (120,000 ms)
        const val OFF_PATH_THRESHOLD_MS = 120_000L
        const val POLLING_INTERVAL_MS = 15_000L // 15 seconds
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_MONITORING) {
            stopMonitoringService()
            return START_NOT_STICKY
        }

        val journeyId = intent?.getStringExtra(EXTRA_JOURNEY_ID)
        val userId = intent?.getStringExtra(EXTRA_USER_ID)

        if (journeyId.isNullOrEmpty() || userId.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentJourneyId = journeyId
        currentUserId = userId

        startForegroundServiceNotification()
        loadJourneyRouteAndStartTracking(journeyId)

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeHer: Journey Active")
            .setContentText("Monitoring your route & safety in real time...")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun loadJourneyRouteAndStartTracking(journeyId: String) {
        serviceScope.launch {
            try {
                val doc = firestore.collection("journeys").document(journeyId).get().await()
                if (doc.exists()) {
                    val journey = doc.toObject(Journey::class.java)
                    if (journey != null) {
                        routePoints = journey.polylinePoints.map { LatLng(it.lat, it.lng) }
                        Log.d("JourneyService", "Loaded ${routePoints.size} route polyline points for journey $journeyId")
                    }
                }
            } catch (e: Exception) {
                Log.e("JourneyService", "Error loading journey doc: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                startLocationUpdates()
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, POLLING_INTERVAL_MS)
            .setMinUpdateIntervalMillis(10_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                processNewLocation(location.latitude, location.longitude)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("JourneyService", "Location permission missing: ${e.message}")
        }
    }

    private fun processNewLocation(lat: Double, lng: Double) {
        val journeyId = currentJourneyId ?: return
        val currentLatLng = LatLng(lat, lng)
        val timestamp = System.currentTimeMillis()

        serviceScope.launch {
            try {
                // Location, journey ping, and user location share one offline transaction shape.
                currentUserId?.let { uid -> offlineSync.writeLocationPing(uid, journeyId, lat, lng, timestamp) }

                // 2. Check route deviation via PolyUtil (150m tolerance, geodesic=true)
                if (routePoints.isNotEmpty()) {
                    val isOnPath = PolyUtil.isLocationOnPath(currentLatLng, routePoints, true, 150.0)

                    if (isOnPath) {
                        // User is on path -> reset timer
                        offPathStartTimestamp = null
                        if (isDeviationAlertTriggered) {
                            isDeviationAlertTriggered = false
                            offlineSync.writeJourneyUpdate(journeyId, mapOf("isDeviated" to false, "deviationAlertActive" to false), "deviation_flag")
                        }
                    } else {
                        // User is OFF path
                        if (offPathStartTimestamp == null) {
                            offPathStartTimestamp = timestamp
                        }
                        val durationOffPath = timestamp - (offPathStartTimestamp ?: timestamp)
                        Log.w("JourneyService", "Off path for ${durationOffPath / 1000}s (threshold 120s)")

                        offlineSync.writeJourneyUpdate(journeyId, mapOf("isDeviated" to true), "deviation_flag")

                        if (durationOffPath >= OFF_PATH_THRESHOLD_MS && !isDeviationAlertTriggered) {
                            isDeviationAlertTriggered = true
                            // Set alert active flag in Firestore so Android UI triggers popup dialog
                            offlineSync.writeJourneyUpdate(journeyId, mapOf("deviationAlertActive" to true), "deviation_flag")
                            showDeviationNotification()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("JourneyService", "Error processing location update: ${e.message}")
            }
        }
    }

    private fun showDeviationNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ Route Deviation Alert")
            .setContentText("You've deviated from your planned route. Are you safe?")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(DEVIATION_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Journey Monitoring Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Route Deviation Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notification channel for route deviation safety alerts"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun stopMonitoringService() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopMonitoringService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
