package com.syncone.health.presentation.auth

import androidx.lifecycle.ViewModel
import com.syncone.health.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockManager: AppLockManager
) : ViewModel() {

    private val _pinInput = MutableStateFlow("")
    val pinInput: StateFlow<String> = _pinInput.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onPinChanged(pin: String) {
        _pinInput.value = pin
        _error.value = null
    }

    fun verifyPin(): Boolean {
        val isValid = appLockManager.verifyPin(_pinInput.value)
        if (!isValid) {
            _error.value = "Invalid PIN"
            _pinInput.value = ""
        }
        return isValid
    }

    fun isPinSet(): Boolean {
        return appLockManager.isPinSet()
    }
}
