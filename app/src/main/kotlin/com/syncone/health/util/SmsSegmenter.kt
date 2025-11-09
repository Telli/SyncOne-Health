package com.syncone.health.util

import android.telephony.SmsManager

/**
 * Handles SMS segmentation for multipart messages.
 */
object SmsSegmenter {

    /**
     * Split message into SMS parts if longer than 160 characters.
     * Returns list of message parts.
     */
    fun splitMessage(message: String): List<String> {
        // Truncate if exceeds max length
        val truncated = if (message.length > Constants.MAX_SMS_LENGTH) {
            message.substring(0, Constants.MAX_SMS_LENGTH)
        } else {
            message
        }

        // Use SmsManager to handle proper segmentation
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(truncated)

        return parts ?: listOf(truncated)
    }

    /**
     * Check if message needs to be split.
     */
    fun needsSplit(message: String): Boolean {
        return message.length > Constants.SMS_PART_LENGTH
    }

    /**
     * Get number of SMS parts needed.
     */
    fun getPartCount(message: String): Int {
        val truncated = if (message.length > Constants.MAX_SMS_LENGTH) {
            message.substring(0, Constants.MAX_SMS_LENGTH)
        } else {
            message
        }

        return when {
            truncated.length <= Constants.SMS_PART_LENGTH -> 1
            truncated.length <= Constants.SMS_PART_LENGTH * 2 -> 2
            else -> 3
        }
    }

    /**
     * Truncate message to fit within max SMS length.
     */
    fun truncate(message: String): String {
        return if (message.length > Constants.MAX_SMS_LENGTH) {
            message.substring(0, Constants.MAX_SMS_LENGTH - 3) + "..."
        } else {
            message
        }
    }
}
