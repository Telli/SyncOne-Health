package com.syncone.health.data.local.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted SharedPreferences for app settings and auth state.
 */
class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "syncone_app_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // App Lock
    fun setLastAuthTime(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_AUTH_TIME, timestamp).apply()
    }

    fun getLastAuthTime(): Long {
        return sharedPreferences.getLong(KEY_LAST_AUTH_TIME, 0L)
    }

    fun setPinHash(hash: String) {
        sharedPreferences.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun getPinHash(): String? {
        return sharedPreferences.getString(KEY_PIN_HASH, null)
    }

    fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    // CHW Info
    fun setChwId(id: String) {
        sharedPreferences.edit().putString(KEY_CHW_ID, id).apply()
    }

    fun getChwId(): String? {
        return sharedPreferences.getString(KEY_CHW_ID, null)
    }

    fun setChwName(name: String) {
        sharedPreferences.edit().putString(KEY_CHW_NAME, name).apply()
    }

    fun getChwName(): String? {
        return sharedPreferences.getString(KEY_CHW_NAME, null)
    }

    // Service State
    fun setServiceEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun isServiceEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, true)
    }

    // Rate Limiting
    fun recordSmsTimestamp(phoneNumber: String, timestamp: Long) {
        val key = "$KEY_SMS_TIMESTAMPS_PREFIX$phoneNumber"
        val existing = sharedPreferences.getString(key, "") ?: ""
        val updated = if (existing.isEmpty()) {
            timestamp.toString()
        } else {
            "$existing,$timestamp"
        }
        sharedPreferences.edit().putString(key, updated).apply()
    }

    fun getSmsTimestamps(phoneNumber: String): List<Long> {
        val key = "$KEY_SMS_TIMESTAMPS_PREFIX$phoneNumber"
        val timestamps = sharedPreferences.getString(key, "") ?: ""
        return if (timestamps.isEmpty()) {
            emptyList()
        } else {
            timestamps.split(",").mapNotNull { it.toLongOrNull() }
        }
    }

    fun clearOldSmsTimestamps(phoneNumber: String, cutoffTime: Long) {
        val key = "$KEY_SMS_TIMESTAMPS_PREFIX$phoneNumber"
        val timestamps = getSmsTimestamps(phoneNumber).filter { it >= cutoffTime }
        val updated = timestamps.joinToString(",")
        sharedPreferences.edit().putString(key, updated).apply()
    }

    companion object {
        private const val KEY_LAST_AUTH_TIME = "last_auth_time"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_CHW_ID = "chw_id"
        private const val KEY_CHW_NAME = "chw_name"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_SMS_TIMESTAMPS_PREFIX = "sms_timestamps_"
    }
}
