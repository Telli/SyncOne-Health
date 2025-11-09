package com.syncone.health.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages encryption keys using Android Keystore.
 * Generates and retrieves AES-256 key for database encryption.
 */
class EncryptionKeyManager(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "syncone_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get or generate database encryption key.
     * Returns raw bytes for SQLCipher passphrase.
     */
    fun getDatabaseKey(): ByteArray {
        val storedKey = securePrefs.getString(KEY_DATABASE_PASSPHRASE, null)
        if (storedKey != null) {
            return android.util.Base64.decode(storedKey, android.util.Base64.DEFAULT)
        }

        // Generate new key
        val key = generateSecureKey()
        val encoded = android.util.Base64.encodeToString(key, android.util.Base64.DEFAULT)
        securePrefs.edit().putString(KEY_DATABASE_PASSPHRASE, encoded).apply()
        return key
    }

    private fun generateSecureKey(): ByteArray {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(256) // AES-256
        return keyGenerator.generateKey().encoded
    }

    companion object {
        private const val KEY_DATABASE_PASSPHRASE = "db_passphrase"
    }
}
