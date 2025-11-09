package com.syncone.health.security

import androidx.fragment.app.FragmentActivity
import com.syncone.health.data.local.preferences.SecurePreferences
import com.syncone.health.util.Constants
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app lock functionality.
 * Handles biometric and PIN authentication with auto-lock timeout.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val biometricHelper: BiometricHelper,
    private val securePreferences: SecurePreferences
) {

    /**
     * Check if app should be locked based on timeout.
     */
    fun shouldLock(): Boolean {
        val lastAuthTime = securePreferences.getLastAuthTime()
        if (lastAuthTime == 0L) {
            return true // Never authenticated
        }

        val elapsed = System.currentTimeMillis() - lastAuthTime
        return elapsed > Constants.APP_LOCK_TIMEOUT_MS
    }

    /**
     * Update last authentication time.
     */
    fun updateAuthTime() {
        securePreferences.setLastAuthTime(System.currentTimeMillis())
        Timber.d("Auth time updated")
    }

    /**
     * Authenticate with biometric if available, otherwise require PIN.
     */
    fun requireAuthentication(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFallbackToPin: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (securePreferences.isBiometricEnabled() && biometricHelper.isBiometricAvailable()) {
            // Try biometric first
            biometricHelper.authenticate(
                activity = activity,
                onSuccess = {
                    updateAuthTime()
                    onSuccess()
                },
                onError = { error ->
                    Timber.w("Biometric auth error, falling back to PIN: $error")
                    onFallbackToPin()
                },
                onFailed = {
                    Timber.w("Biometric auth failed, falling back to PIN")
                    onFallbackToPin()
                }
            )
        } else {
            // Fallback to PIN
            onFallbackToPin()
        }
    }

    /**
     * Verify PIN.
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = securePreferences.getPinHash()
        if (storedHash == null) {
            // No PIN set, create one
            setPin(pin)
            return true
        }

        val pinHash = hashPin(pin)
        val isValid = pinHash == storedHash

        if (isValid) {
            updateAuthTime()
            Timber.d("PIN verification successful")
        } else {
            Timber.w("PIN verification failed")
        }

        return isValid
    }

    /**
     * Set new PIN.
     */
    fun setPin(pin: String) {
        val hash = hashPin(pin)
        securePreferences.setPinHash(hash)
        Timber.d("PIN set")
    }

    /**
     * Check if PIN is set.
     */
    fun isPinSet(): Boolean {
        return securePreferences.getPinHash() != null
    }

    /**
     * Hash PIN using SHA-256.
     */
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Toggle biometric authentication.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        securePreferences.setBiometricEnabled(enabled)
    }

    /**
     * Check if biometric is enabled.
     */
    fun isBiometricEnabled(): Boolean {
        return securePreferences.isBiometricEnabled()
    }
}
