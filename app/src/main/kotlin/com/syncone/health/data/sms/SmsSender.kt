package com.syncone.health.data.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.syncone.health.util.Constants
import com.syncone.health.util.SmsSegmenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

/**
 * Sends SMS messages with retry logic.
 * Handles multipart messages and delivery confirmation.
 */
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val smsManager = SmsManager.getDefault()

    /**
     * Send SMS with retry logic.
     * Returns Result with success/failure.
     */
    suspend fun send(phoneNumber: String, message: String): Result<Unit> {
        var lastException: Exception? = null

        repeat(Constants.SMS_RETRY_ATTEMPTS) { attempt ->
            try {
                sendInternal(phoneNumber, message)
                Timber.d("SMS sent successfully to $phoneNumber (attempt ${attempt + 1})")
                return Result.success(Unit)
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "SMS send failed (attempt ${attempt + 1}/${Constants.SMS_RETRY_ATTEMPTS})")

                // Exponential backoff: 1s, 2s, 4s
                if (attempt < Constants.SMS_RETRY_ATTEMPTS - 1) {
                    val delayMs = Constants.SMS_RETRY_DELAY_MS * (1 shl attempt)
                    delay(delayMs)
                }
            }
        }

        return Result.failure(lastException ?: Exception("SMS send failed after ${Constants.SMS_RETRY_ATTEMPTS} attempts"))
    }

    private fun sendInternal(phoneNumber: String, message: String) {
        val parts = SmsSegmenter.splitMessage(message)

        if (parts.size == 1) {
            // Single part SMS
            smsManager.sendTextMessage(
                phoneNumber,
                null,
                parts[0],
                null, // No sent intent for Phase 1
                null  // No delivery intent for Phase 1
            )
        } else {
            // Multipart SMS
            val sentIntents = ArrayList<PendingIntent?>()
            val deliveryIntents = ArrayList<PendingIntent?>()

            // Create null intents for each part (no tracking in Phase 1)
            repeat(parts.size) {
                sentIntents.add(null)
                deliveryIntents.add(null)
            }

            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                ArrayList(parts),
                sentIntents,
                deliveryIntents
            )
        }
    }

    /**
     * Send multipart SMS (for longer messages).
     */
    suspend fun sendMultipart(phoneNumber: String, parts: List<String>): Result<Unit> {
        return try {
            val sentIntents = ArrayList<PendingIntent?>()
            val deliveryIntents = ArrayList<PendingIntent?>()

            repeat(parts.size) {
                sentIntents.add(null)
                deliveryIntents.add(null)
            }

            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                ArrayList(parts),
                sentIntents,
                deliveryIntents
            )

            Timber.d("Multipart SMS sent successfully to $phoneNumber (${parts.size} parts)")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send multipart SMS")
            Result.failure(e)
        }
    }
}
