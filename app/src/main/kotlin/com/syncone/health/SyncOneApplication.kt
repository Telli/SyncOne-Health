package com.syncone.health

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.syncone.health.data.local.ml.ModelManager
import com.syncone.health.service.ThreadExpirationWorker
import com.syncone.health.util.Constants
import com.syncone.health.util.GuidelineSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application class for SyncOne Health.
 * Initializes Timber, notification channels, ML models, and periodic workers.
 */
@HiltAndroidApp
class SyncOneApplication : Application() {

    @Inject
    lateinit var modelManager: ModelManager

    @Inject
    lateinit var guidelineSeeder: GuidelineSeeder

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

        // Initialize ML models asynchronously
        initializeModels()
    }

    /**
     * Initialize ML models in background.
     * Models will load on app start, but app remains functional if loading fails.
     */
    private fun initializeModels() {
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            try {
                Timber.i("Initializing ML models...")
                modelManager.initialize()

                // Seed medical guidelines after models are ready
                if (modelManager.isEmbeddingReady()) {
                    guidelineSeeder.seedGuidelines()
                }

                Timber.i("ML initialization complete")
            } catch (e: Exception) {
                Timber.e(e, "ML model initialization failed - app will use cloud fallback")
            }
        }
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
