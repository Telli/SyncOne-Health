package com.syncone.health.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.syncone.health.data.local.preferences.SecurePreferences
import com.syncone.health.service.SmsProcessingWorker
import com.syncone.health.util.Constants
import com.syncone.health.util.PhoneNumberFormatter
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives incoming SMS messages.
 * Handles rate limiting and queues messages for processing.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rateLimiter: RateLimiter

    @Inject
    lateinit var smsSender: SmsSender

    @Inject
    lateinit var phoneNumberFormatter: PhoneNumberFormatter

    @Inject
    lateinit var securePreferences: SecurePreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        // Check if service is enabled
        if (!securePreferences.isServiceEnabled()) {
            Timber.d("SMS Gateway service is disabled, ignoring incoming SMS")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }

        // Combine message parts (for multipart SMS)
        val sender = messages[0].originatingAddress ?: return
        val messageBody = messages.joinToString("") { it.messageBody ?: "" }

        Timber.d("Received SMS from $sender: $messageBody")

        try {
            // Normalize phone number to E.164
            val normalizedPhone = phoneNumberFormatter.toE164(sender)

            // Check rate limits
            if (!rateLimiter.isAllowed(normalizedPhone)) {
                Timber.w("Rate limit exceeded for $normalizedPhone")
                sendRateLimitReply(normalizedPhone)
                return
            }

            // Record this SMS for rate limiting
            rateLimiter.recordSms(normalizedPhone)

            // Enqueue for processing via WorkManager
            enqueueProcessing(context, normalizedPhone, messageBody)

        } catch (e: Exception) {
            Timber.e(e, "Error processing incoming SMS")
        }
    }

    private fun enqueueProcessing(context: Context, phoneNumber: String, message: String) {
        val workRequest = OneTimeWorkRequestBuilder<SmsProcessingWorker>()
            .setInputData(
                workDataOf(
                    SmsProcessingWorker.KEY_PHONE_NUMBER to phoneNumber,
                    SmsProcessingWorker.KEY_MESSAGE to message
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Timber.d("Enqueued SMS processing work for $phoneNumber")
    }

    private fun sendRateLimitReply(phoneNumber: String) {
        // Send rate limit auto-reply (fire and forget)
        // Note: This is synchronous, which is okay for BroadcastReceiver
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(
                phoneNumber,
                null,
                Constants.AUTO_REPLY_RATE_LIMIT,
                null,
                null
            )
            Timber.d("Sent rate limit auto-reply to $phoneNumber")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send rate limit auto-reply")
        }
    }
}
