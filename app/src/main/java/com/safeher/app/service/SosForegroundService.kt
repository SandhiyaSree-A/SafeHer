package com.safeher.app.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.safeher.app.MainActivity
import com.safeher.app.data.repository.SosRepository
import com.safeher.app.data.offline.ConnectivityRepository
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource


class SosForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "safeher_sos_channel"
    private val NOTIFICATION_ID = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SOS) {
            stopSelf()
            return START_NOT_STICKY
        }

        val userId = intent?.getStringExtra(EXTRA_USER_ID) ?: ""
        val userName = intent?.getStringExtra(EXTRA_USER_NAME) ?: "User"
        val userPhone = intent?.getStringExtra(EXTRA_USER_PHONE) ?: ""

        val notification = buildNotification("SafeHer SOS Active - Triggering emergency alerts...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            triggerSosFlow(userId, userName, userPhone)
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun triggerSosFlow(userId: String, userName: String, userPhone: String) {
        if (userId.isBlank()) return

        val locationClient = LocationServices.getFusedLocationProviderClient(this)
        var lat = 0.0
var lng = 0.0
var hasFix = false
try {
    val fresh = withTimeoutOrNull(5_000L) {
        locationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token
        ).awaitResult()
    }
    val loc = fresh ?: locationClient.lastLocation.awaitResult()
    if (loc != null) { lat = loc.latitude; lng = loc.longitude; hasFix = true }
} catch (e: Exception) { }

        val sosRepository = SosRepository()
        sosRepository.createSosAlert(
            userId = userId,
            userName = userName,
            userPhone = userPhone,
            lat = lat,
            lng = lng,
            onSuccess = { alertId ->
                updateNotification("SOS Active! Alert created. Dispatching SMS to contacts...")
            },
            onFailure = {
                updateNotification("SOS Active! Failed to sync alert to cloud, sending SMS...")
            },
            onQueued = {
                updateNotification("No internet detected — SOS sent via SMS to your contacts. It will also appear in Security Room once you're back online.")
            }
        )

        // Query emergency contacts subcollection
        try {
            val firestore = FirebaseFirestore.getInstance()
            val contactsQuery = firestore.collection("users")
                .document(userId)
                .collection("emergency_contacts")
            val contactsSnapshot = if (ConnectivityRepository.get(this).currentlyOnline()) {
                contactsQuery.get()
            } else {
                contactsQuery.get(Source.CACHE)
            }
                .awaitResult()

val locationText = if (hasFix) "Location: https://maps.google.com/?q=$lat,$lng ." else "Location unavailable (no GPS fix)."
val smsMessage = "SOS EMERGENCY: $userName needs immediate help! $locationText Sent via SafeHer."

            contactsSnapshot?.documents?.forEach { doc ->
                val phone = doc.getString("phone") ?: ""
                if (phone.isNotBlank()) {
                    sendSms(phone, smsMessage)
                }
            }

            updateNotification("SOS ACTIVE! Emergency contacts notified via SMS.")
        } catch (e: Exception) {
            updateNotification("SOS ACTIVE! Emergency contacts alert attempted.")
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SafeHer SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active SOS alert notification channel"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SosForegroundService::class.java).apply {
            action = ACTION_STOP_SOS
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeHer Emergency SOS")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel SOS", stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_USER_PHONE = "extra_user_phone"
        const val ACTION_STOP_SOS = "action_stop_sos"
    }
}

// Helper extension function for Task await in coroutine
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result, null) }
        addOnFailureListener { e -> cont.resume(null, null) }
    }
