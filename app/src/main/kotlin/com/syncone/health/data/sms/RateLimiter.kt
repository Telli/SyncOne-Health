package com.syncone.health.data.sms

import com.syncone.health.data.local.preferences.SecurePreferences
import com.syncone.health.util.Constants
import javax.inject.Inject

/**
 * Rate limiter for incoming SMS.
 * Enforces 20 messages/hour, 100 messages/day per phone number.
 */
class RateLimiter @Inject constructor(
    private val securePrefs: SecurePreferences
) {

    /**
     * Check if phone number has exceeded rate limits.
     * Returns true if allowed, false if rate limited.
     */
    fun isAllowed(phoneNumber: String): Boolean {
        val now = System.currentTimeMillis()

        // Get all timestamps for this number
        val timestamps = securePrefs.getSmsTimestamps(phoneNumber)

        // Clean up old timestamps (older than 24 hours)
        val dayAgo = now - Constants.RATE_LIMIT_DAY_MS
        securePrefs.clearOldSmsTimestamps(phoneNumber, dayAgo)

        // Filter timestamps within windows
        val hourAgo = now - Constants.RATE_LIMIT_HOUR_MS
        val recentTimestamps = timestamps.filter { it >= dayAgo }
        val hourlyCount = recentTimestamps.count { it >= hourAgo }
        val dailyCount = recentTimestamps.size

        // Check limits
        return hourlyCount < Constants.MAX_SMS_PER_HOUR &&
                dailyCount < Constants.MAX_SMS_PER_DAY
    }

    /**
     * Record a new SMS timestamp.
     */
    fun recordSms(phoneNumber: String) {
        val now = System.currentTimeMillis()
        securePrefs.recordSmsTimestamp(phoneNumber, now)
    }

    /**
     * Get remaining quota information.
     */
    fun getQuota(phoneNumber: String): RateQuota {
        val now = System.currentTimeMillis()
        val timestamps = securePrefs.getSmsTimestamps(phoneNumber)

        val hourAgo = now - Constants.RATE_LIMIT_HOUR_MS
        val dayAgo = now - Constants.RATE_LIMIT_DAY_MS

        val recentTimestamps = timestamps.filter { it >= dayAgo }
        val hourlyCount = recentTimestamps.count { it >= hourAgo }
        val dailyCount = recentTimestamps.size

        return RateQuota(
            hourlyUsed = hourlyCount,
            hourlyLimit = Constants.MAX_SMS_PER_HOUR,
            dailyUsed = dailyCount,
            dailyLimit = Constants.MAX_SMS_PER_DAY
        )
    }

    data class RateQuota(
        val hourlyUsed: Int,
        val hourlyLimit: Int,
        val dailyUsed: Int,
        val dailyLimit: Int
    ) {
        val hourlyRemaining: Int get() = (hourlyLimit - hourlyUsed).coerceAtLeast(0)
        val dailyRemaining: Int get() = (dailyLimit - dailyUsed).coerceAtLeast(0)
    }
}
