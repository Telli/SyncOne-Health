package com.syncone.health.util

import android.content.Context
import android.telephony.TelephonyManager

/**
 * Formats phone numbers to E.164 standard (+1234567890).
 */
class PhoneNumberFormatter(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    /**
     * Convert phone number to E.164 format.
     * Auto-detects country code from SIM if available.
     */
    fun toE164(phoneNumber: String): String {
        // Remove all non-digit characters except +
        val cleaned = phoneNumber.replace(Regex("[^+\\d]"), "")

        // Already in E.164 format
        if (cleaned.startsWith("+")) {
            return cleaned
        }

        // Get country code from SIM
        val countryCode = getCountryCode()

        // If starts with country code digits, add +
        if (cleaned.startsWith(countryCode.removePrefix("+"))) {
            return "+$cleaned"
        }

        // Otherwise, prepend country code
        return "$countryCode$cleaned"
    }

    /**
     * Get country code from SIM or default to Sierra Leone (+232).
     */
    private fun getCountryCode(): String {
        return try {
            val simCountryIso = telephonyManager?.simCountryIso?.uppercase()
            when (simCountryIso) {
                "SL" -> "+232" // Sierra Leone
                "US" -> "+1"   // United States
                "GB" -> "+44"  // United Kingdom
                "NG" -> "+234" // Nigeria
                "GH" -> "+233" // Ghana
                "KE" -> "+254" // Kenya
                // Add more as needed
                else -> Constants.DEFAULT_COUNTRY_CODE
            }
        } catch (e: Exception) {
            Constants.DEFAULT_COUNTRY_CODE
        }
    }

    /**
     * Format for display (e.g., +232 76 123 456).
     */
    fun formatForDisplay(e164Number: String): String {
        if (!e164Number.startsWith("+")) return e164Number

        // Simple formatting: +XXX XX XXX XXX
        return when {
            e164Number.startsWith("+232") -> {
                val rest = e164Number.substring(4)
                if (rest.length >= 8) {
                    "+232 ${rest.substring(0, 2)} ${rest.substring(2, 5)} ${rest.substring(5)}"
                } else {
                    e164Number
                }
            }
            e164Number.startsWith("+1") -> {
                val rest = e164Number.substring(2)
                if (rest.length == 10) {
                    "+1 (${rest.substring(0, 3)}) ${rest.substring(3, 6)}-${rest.substring(6)}"
                } else {
                    e164Number
                }
            }
            else -> e164Number
        }
    }

    /**
     * Mask phone number for privacy (show last 4 digits only).
     * Note: We store unmasked numbers for CHW callbacks, but can mask for display.
     */
    fun maskForPrivacy(e164Number: String): String {
        if (e164Number.length <= 4) return e164Number
        val lastFour = e164Number.takeLast(4)
        val masked = "*".repeat(e164Number.length - 4)
        return "$masked$lastFour"
    }
}
