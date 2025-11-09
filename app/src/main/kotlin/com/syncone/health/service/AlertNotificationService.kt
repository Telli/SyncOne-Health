package com.syncone.health.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.syncone.health.R
import com.syncone.health.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Firebase Cloud Messaging service for receiving alert notifications
 *
 * To configure FCM:
 * 1. Add google-services.json to app/
 * 2. Add FCM dependencies to build.gradle.kts
 * 3. Subscribe to device topic: FirebaseMessaging.getInstance().subscribeToTopic("device_${deviceId}")
 */
@AndroidEntryPoint
class AlertNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Timber.d("FCM message received from: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            val action = remoteMessage.data["action"]

            when (action) {
                "ALERT" -> handleAlertNotification(remoteMessage.data)
                "REMOTE_WIPE" -> handleRemoteWipe(remoteMessage.data)
                else -> Timber.w("Unknown action: $action")
            }
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "SyncOne Health",
                body = notification.body ?: "",
                urgency = remoteMessage.data["urgency"] ?: "NORMAL"
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("New FCM token: $token")

        // TODO: Send token to backend for registration
        // In production, you would send this to your backend:
        // apiClient.registerDeviceToken(token)
    }

    private fun handleAlertNotification(data: Map<String, String>) {
        val alertId = data["alert_id"] ?: return
        val urgency = data["urgency"] ?: "NORMAL"
        val patientPhone = data["patient_phone"] ?: "Unknown"
        val message = data["message"] ?: ""

        Timber.w("ALERT received: $urgency - Patient: $patientPhone")

        showNotification(
            title = "$urgency ALERT",
            body = "Patient $patientPhone: $message",
            urgency = urgency
        )
    }

    private fun handleRemoteWipe(data: Map<String, String>) {
        val authToken = data["auth_token"] ?: return

        Timber.e("REMOTE WIPE command received")

        // Verify auth token (in production, verify against stored secret)
        // For now, just show critical notification
        showNotification(
            title = "⚠️ SECURITY ALERT",
            body = "Remote wipe initiated by administrator. Contact support immediately.",
            urgency = "CRITICAL"
        )

        // TODO: Implement actual wipe logic
        // In production, you would:
        // 1. Verify auth_token
        // 2. Clear all local databases
        // 3. Clear SharedPreferences
        // 4. Clear app cache
        // 5. Optionally factory reset device (requires device admin)
    }

    private fun showNotification(title: String, body: String, urgency: String) {
        val channelId = when (urgency) {
            "CRITICAL" -> CHANNEL_ID_CRITICAL
            "URGENT" -> CHANNEL_ID_URGENT
            else -> CHANNEL_ID_NORMAL
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(when (urgency) {
                "CRITICAL" -> NotificationCompat.PRIORITY_MAX
                "URGENT" -> NotificationCompat.PRIORITY_HIGH
                else -> NotificationCompat.PRIORITY_DEFAULT
            })

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channels for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels(notificationManager)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical patient alerts requiring immediate attention"
                enableVibration(true)
                enableLights(true)
            }

            val urgentChannel = NotificationChannel(
                CHANNEL_ID_URGENT,
                "Urgent Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Urgent patient alerts"
                enableVibration(true)
            }

            val normalChannel = NotificationChannel(
                CHANNEL_ID_NORMAL,
                "Normal Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General notifications"
            }

            notificationManager.createNotificationChannel(criticalChannel)
            notificationManager.createNotificationChannel(urgentChannel)
            notificationManager.createNotificationChannel(normalChannel)
        }
    }

    companion object {
        private const val CHANNEL_ID_CRITICAL = "syncone_alerts_critical"
        private const val CHANNEL_ID_URGENT = "syncone_alerts_urgent"
        private const val CHANNEL_ID_NORMAL = "syncone_alerts_normal"
    }
}
