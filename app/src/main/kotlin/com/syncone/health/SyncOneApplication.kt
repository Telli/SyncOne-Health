package com.syncone.health

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.syncone.health.service.ThreadExpirationWorker
import com.syncone.health.util.Constants
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Application class for SyncOne Health.
 * Initializes Timber, notification channels, and periodic workers.
 */
@HiltAndroidApp
class SyncOneApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("SyncOne Health starting...")

        // Create notification channels
        createNotificationChannels()

        // Schedule periodic workers
        schedulePeriodicWorkers()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Service notification channel
            val serviceChannel = NotificationChannel(
                Constants.CHANNEL_SERVICE,
                "SMS Gateway Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when SMS gateway is active"
            }
            notificationManager.createNotificationChannel(serviceChannel)

            // Critical alerts channel
            val alertsChannel = NotificationChannel(
                Constants.CHANNEL_CRITICAL_ALERTS,
                "Critical Health Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent medical queries requiring immediate attention"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertsChannel)

            Timber.d("Notification channels created")
        }
    }

    private fun schedulePeriodicWorkers() {
        val workManager = WorkManager.getInstance(this)

        // Thread expiration worker (runs every hour)
        val expirationWork = PeriodicWorkRequestBuilder<ThreadExpirationWorker>(
            Constants.THREAD_EXPIRATION_WORK_INTERVAL_HOURS,
            TimeUnit.HOURS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "thread_expiration",
            ExistingPeriodicWorkPolicy.KEEP,
            expirationWork
        )

        Timber.d("Periodic workers scheduled")
    }
}
