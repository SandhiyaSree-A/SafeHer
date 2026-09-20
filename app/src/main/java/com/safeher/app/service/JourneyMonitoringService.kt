package com.safeher.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import com.safeher.app.MainActivity
import com.safeher.app.data.model.EmergencyContact
import com.safeher.app.data.model.Journey
import com.safeher.app.data.offline.OfflineSyncRepository
import com.safeher.app.util.RouteCache
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.net.URL
import org.json.JSONObject

class JourneyMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val firestore = FirebaseFirestore.getInstance()
    private val offlineSync by lazy { OfflineSyncRepository.get(this) }

    private var currentJourneyId: String? = null
    private var currentUserId: String? = null
    private var routePoints: List<LatLng> = emptyList()

    private var lastLocationPing: LatLng? = null
    private var lastMovementTimestamp: Long = System.currentTimeMillis()
    private var networkLossTimestamp: Long? = null

    private var offPathStartTimestamp: Long? = null
    private var isDeviationAlertTriggered: Boolean = false
    private var isEmergencySnapshotSent: Boolean = false

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val ACTION_START_MONITORING = "com.safeher.app.action.START_JOURNEY_MONITORING"
        const val ACTION_STOP_MONITORING = "com.safeher.app.action.STOP_JOURNEY_MONITORING"
        const val EXTRA_JOURNEY_ID = "extra_journey_id"
        const val EXTRA_USER_ID = "extra_user_id"

        const val CHANNEL_ID = "journey_monitoring_channel"
        const val ALERT_CHANNEL_ID = "journey_deviation_alert_channel"
        const val NOTIFICATION_ID = 2001
        const val DEVIATION_NOTIFICATION_ID = 2002

        const val OFF_PATH_THRESHOLD_MS = 120_000L      // 2 minutes off-path
        const val INACTIVITY_THRESHOLD_MS = 300_000L    // 5 minutes movement inactivity
        const val NETWORK_LOSS_THRESHOLD_MS = 180_000L  // 3 minutes network loss
        const val POLLING_INTERVAL_MS = 15_000L         // 15 seconds location ping
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannels()
        registerNetworkWatchdog()
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
            .setContentTitle("SafeHer: Real-Time Journey Monitoring")
            .setContentText("Watchdog active for Network Loss, Inactivity & Safety Deviations...")
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
            val cached = RouteCache.load(this@JourneyMonitoringService, journeyId)
            if (cached.isNotEmpty()) {
                routePoints = cached.map { LatLng(it.lat, it.lng) }
                Log.d("JourneyService", "Loaded ${routePoints.size} route points from cache for $journeyId")
            } else {
                try {
                    val doc = firestore.collection("journeys").document(journeyId).get().await()
                    if (doc.exists()) {
                        val journey = doc.toObject(Journey::class.java)
                        if (journey != null) {
                            routePoints = journey.polylinePoints.map { LatLng(it.lat, it.lng) }
                            Log.d("JourneyService", "Loaded ${routePoints.size} polyline points for journey $journeyId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("JourneyService", "Error loading journey doc: ${e.message}")
                }
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

        // 1. Inactivity Watchdog Check (track movement delta)
        val prevLoc = lastLocationPing
        if (prevLoc != null) {
            val deltaMeters = FloatArray(1)
            android.location.Location.distanceBetween(prevLoc.latitude, prevLoc.longitude, lat, lng, deltaMeters)
            if (deltaMeters[0] > 15.0) {
                lastMovementTimestamp = timestamp // User is moving
            }
        } else {
            lastMovementTimestamp = timestamp
        }
        lastLocationPing = currentLatLng

        val inactiveDuration = timestamp - lastMovementTimestamp
        if (inactiveDuration >= INACTIVITY_THRESHOLD_MS && !isEmergencySnapshotSent) {
            triggerEmergencySnapshotAlert("Inactivity Watchdog: User stopped moving for > 5 mins")
        }

        // 2. Off-Path Deviation Check
        serviceScope.launch {
            try {
                currentUserId?.let { uid -> offlineSync.writeLocationPing(uid, journeyId, lat, lng, timestamp) }

                if (routePoints.isNotEmpty()) {
                    val isOnPath = PolyUtil.isLocationOnPath(currentLatLng, routePoints, true, 150.0)

                    if (isOnPath) {
                        offPathStartTimestamp = null
                        if (isDeviationAlertTriggered) {
                            isDeviationAlertTriggered = false
                            offlineSync.writeJourneyUpdate(journeyId, mapOf("isDeviated" to false, "deviationAlertActive" to false), "deviation_flag")
                        }
                    } else {
                        if (offPathStartTimestamp == null) {
                            offPathStartTimestamp = timestamp
                        }
                        val durationOffPath = timestamp - (offPathStartTimestamp ?: timestamp)
                        offlineSync.writeJourneyUpdate(journeyId, mapOf("isDeviated" to true), "deviation_flag")

                        if (durationOffPath >= OFF_PATH_THRESHOLD_MS && !isDeviationAlertTriggered) {
                            isDeviationAlertTriggered = true
                            offlineSync.writeJourneyUpdate(journeyId, mapOf("deviationAlertActive" to true), "deviation_flag")
                            showDeviationNotification()
                            triggerEmergencySnapshotAlert("Route Deviation Watchdog: Off-path for > 2 mins")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("JourneyService", "Error processing location update: ${e.message}")
            }
        }
    }

    private fun registerNetworkWatchdog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    super.onLost(network)
                    networkLossTimestamp = System.currentTimeMillis()
                    Log.w("JourneyService", "Network connection lost during active journey")

                    serviceScope.launch {
                        delay(NETWORK_LOSS_THRESHOLD_MS)
                        if (networkLossTimestamp != null && !isEmergencySnapshotSent) {
                            val lossDuration = System.currentTimeMillis() - (networkLossTimestamp ?: System.currentTimeMillis())
                            if (lossDuration >= NETWORK_LOSS_THRESHOLD_MS) {
                                triggerEmergencySnapshotAlert("Network Loss Watchdog: Connection lost for > 3 mins")
                            }
                        }
                    }
                }

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    networkLossTimestamp = null
                    Log.i("JourneyService", "Network connection restored")
                }
            }
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
            } catch (e: Exception) {
                Log.e("JourneyService", "Error registering network callback: ${e.message}")
            }
        }
    }

    private fun triggerEmergencySnapshotAlert(reason: String) {
        if (isEmergencySnapshotSent) return
        isEmergencySnapshotSent = true

        serviceScope.launch {
            val uid = currentUserId ?: return@launch
            val loc = lastLocationPing ?: return@launch

            val addressName = reverseGeocodeAddress(loc.latitude, loc.longitude)
            val mapsLink = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"

            val smsMessage = "[SafeHer Emergency Snapshot]\nALERT: $reason\nLast Location: $addressName\nMap: $mapsLink\nTime: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"

            try {
                // Fetch emergency contacts from Firestore
                val snapshot = firestore.collection("users").document(uid).collection("emergency_contacts").get().await()
                val contacts = snapshot.toObjects(EmergencyContact::class.java)

                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    applicationContext.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                for (contact in contacts) {
                    if (contact.phone.isNotBlank()) {
                        smsManager.sendTextMessage(contact.phone, null, smsMessage, null, null)
                        Log.i("JourneyService", "Dispatched Emergency Snapshot SMS to ${contact.name} (${contact.phone})")
                    }
                }
            } catch (e: Exception) {
                Log.e("JourneyService", "Error sending emergency snapshot SMS: ${e.message}")
            }
        }
    }

    private fun reverseGeocodeAddress(lat: Double, lng: Double): String {
        return try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=jsonv2")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "SafeHer-App/1.0")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                json.optString("display_name", "Lat $lat, Lng $lng")
            } else {
                "Lat $lat, Lng $lng"
            }
        } catch (e: Exception) {
            "Lat $lat, Lng $lng"
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
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
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
