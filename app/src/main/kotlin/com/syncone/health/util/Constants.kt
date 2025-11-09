package com.syncone.health.util

/**
 * Application-wide constants.
 */
object Constants {

    // Rate Limits
    const val MAX_SMS_PER_HOUR = 20
    const val MAX_SMS_PER_DAY = 100
    const val RATE_LIMIT_HOUR_MS = 60 * 60 * 1000L
    const val RATE_LIMIT_DAY_MS = 24 * 60 * 60 * 1000L

    // Context Windows
    const val LOCAL_TOKEN_LIMIT = 512
    const val CLOUD_TOKEN_LIMIT = 1024
    const val ROLLING_WINDOW_SIZE = 3 // last 3 turns

    // TTL
    const val THREAD_TTL_MS = 72 * 60 * 60 * 1000L // 72 hours

    // SMS
    const val MAX_SMS_LENGTH = 480 // 3 SMS parts
    const val SMS_PART_LENGTH = 160

    // Security
    const val APP_LOCK_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    // Retry Logic
    const val SMS_RETRY_ATTEMPTS = 3
    const val SMS_RETRY_DELAY_MS = 1000L // 1 second initial delay

    // Background Work
    const val THREAD_EXPIRATION_WORK_INTERVAL_HOURS = 1L

    // Default Country Code
    const val DEFAULT_COUNTRY_CODE = "+232" // Sierra Leone

    // Urgency Keywords
    val CRITICAL_KEYWORDS = setOf(
        "bleeding", "blood", "unconscious", "faint", "fainting", "fainted",
        "seizure", "convulsion", "convulsions", "fit", "fits",
        "labor", "labour", "contractions", "birth", "delivery", "delivering",
        "chest pain", "breathless", "can't breathe", "cannot breathe",
        "severe pain", "unbearable pain", "unresponsive",
        "suicide", "suicidal", "kill myself", "overdose",
        "choking", "drowning", "stroke", "heart attack",
        "severe bleeding", "heavy bleeding", "hemorrhage"
    )

    val URGENT_KEYWORDS = setOf(
        "high fever", "very hot", "burning up", "fever",
        "vomiting", "vomit", "throwing up",
        "diarrhea", "diarrhoea", "loose stool", "watery stool",
        "dehydrated", "dehydration", "very thirsty",
        "injury", "injured", "wound", "cut", "gash",
        "broken", "fracture", "fractured", "broken bone",
        "burn", "burned", "burnt", "burning",
        "swelling", "swollen", "inflammation",
        "rash", "skin problem", "itching badly",
        "difficulty breathing", "hard to breathe",
        "severe headache", "terrible headache",
        "pregnant", "pregnancy problem"
    )

    // Special Commands
    const val RESET_COMMAND = "RESET"

    // Notification Channels
    const val CHANNEL_SERVICE = "sms_gateway_service"
    const val CHANNEL_CRITICAL_ALERTS = "critical_alerts"

    // Auto-Reply Messages
    const val AUTO_REPLY_RATE_LIMIT = "You have reached the message limit. Please try again later."
    const val AUTO_REPLY_DEFAULT = "Thank you for contacting SyncOne Health. A health worker will respond soon."

    // Audit Event Details Keys
    const val AUDIT_KEY_PHONE = "phone_number"
    const val AUDIT_KEY_MESSAGE = "message"
    const val AUDIT_KEY_URGENCY = "urgency_level"
    const val AUDIT_KEY_RATING = "rating"
    const val AUDIT_KEY_ORIGINAL = "original_message"
    const val AUDIT_KEY_EDITED = "edited_message"
}
