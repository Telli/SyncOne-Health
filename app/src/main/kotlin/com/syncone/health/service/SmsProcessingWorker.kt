package com.syncone.health.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.syncone.health.R
import com.syncone.health.data.sms.SmsSender
import com.syncone.health.domain.model.enums.MessageStatus
import com.syncone.health.domain.model.enums.UrgencyLevel
import com.syncone.health.domain.usecase.BuildPromptUseCase
import com.syncone.health.domain.usecase.ManageThreadContextUseCase
import com.syncone.health.domain.usecase.ProcessIncomingSmsUseCase
import com.syncone.health.domain.usecase.SendSmsReplyUseCase
import com.syncone.health.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Background worker that processes incoming SMS messages.
 * Handles AI inference (stub), context management, and reply sending.
 */
@HiltWorker
class SmsProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val processIncomingSmsUseCase: ProcessIncomingSmsUseCase,
    private val sendSmsReplyUseCase: SendSmsReplyUseCase,
    private val manageContextUseCase: ManageThreadContextUseCase,
    private val buildPromptUseCase: BuildPromptUseCase,
    private val smsSender: SmsSender
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val phoneNumber = inputData.getString(KEY_PHONE_NUMBER) ?: return Result.failure()
        val message = inputData.getString(KEY_MESSAGE) ?: return Result.failure()

        Timber.d("Processing SMS from $phoneNumber: $message")

        try {
            // Process incoming SMS
            val processResult = processIncomingSmsUseCase(phoneNumber, message)

            // Handle RESET command
            if (processResult.isResetCommand) {
                val resetReply = "Conversation reset. You can start a new query."
                sendReply(processResult.threadId, phoneNumber, resetReply)
                return Result.success()
            }

            // Show critical alert if needed
            if (processResult.urgencyLevel == UrgencyLevel.CRITICAL) {
                showCriticalAlert(phoneNumber)
            }

            // Get conversation context
            val context = manageContextUseCase.getContext(processResult.threadId)

            // Build prompt
            val prompt = buildPromptUseCase(context, message)

            // Phase 1: Stub AI response
            val aiResponse = generateStubResponse(message)

            // Update context with new turn
            val updatedContext = buildPromptUseCase.buildUpdatedContext(
                threadId = processResult.threadId,
                existingContext = context,
                newUserMessage = message,
                aiResponse = aiResponse
            )
            manageContextUseCase.saveContext(updatedContext)

            // Send reply
            sendReply(processResult.threadId, phoneNumber, aiResponse)

            Timber.d("SMS processing completed for $phoneNumber")
            return Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Error processing SMS")
            return Result.retry()
        }
    }

    private suspend fun sendReply(threadId: Long, phoneNumber: String, message: String) {
        // Save to database
        val messageId = sendSmsReplyUseCase(
            threadId = threadId,
            content = message,
            isManual = false,
            aiConfidence = 0.7f // Stub confidence
        )

        // Actually send SMS
        val sendResult = smsSender.send(phoneNumber, message)

        // Update status
        val status = if (sendResult.isSuccess) {
            MessageStatus.SENT
        } else {
            MessageStatus.FAILED
        }
        sendSmsReplyUseCase.updateStatus(messageId, status)
    }

    /**
     * Phase 1 stub: Simple auto-reply.
     * Phase 2: Replace with actual TFLite inference.
     */
    private fun generateStubResponse(userMessage: String): String {
        return Constants.AUTO_REPLY_DEFAULT
    }

    private fun showCriticalAlert(phoneNumber: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.CHANNEL_CRITICAL_ALERTS,
                "Critical Health Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent medical queries requiring immediate attention"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build notification
        val notification = NotificationCompat.Builder(applicationContext, Constants.CHANNEL_CRITICAL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this
            .setContentTitle(applicationContext.getString(R.string.critical_alert_title))
            .setContentText(applicationContext.getString(R.string.critical_alert_text, phoneNumber))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(phoneNumber.hashCode(), notification)
    }

    companion object {
        const val KEY_PHONE_NUMBER = "phone_number"
        const val KEY_MESSAGE = "message"
    }
}
