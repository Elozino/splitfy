package com.elozino.splitfy.screens.account.security_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.SecurityOverview
import com.elozino.splitfy.repositories.SecurityRepository
import com.elozino.splitfy.security.AppLockController
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecurityState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    val overview: SecurityOverview? = null,
    val appLockEnabled: Boolean = false,
    val appLockTimeoutMinutes: Int = 1,
    val isDisableDialogVisible: Boolean = false,
    /** Disable with the account password instead of a code. */
    val disableWithPassword: Boolean = false,
    val disableInput: String = "",
    val isDisabling: Boolean = false,
    val disableError: String? = null,
    /** One-off confirmation for a snackbar. */
    val message: String? = null,
) {
    val canSubmitDisable: Boolean get() = !isDisabling && disableInput.isNotBlank()
}

/**
 * The Security screen: what the server knows (password, 2FA) and what only this
 * device knows (App lock). Delete account stays on [com.elozino.splitfy.screens.account.shared.AccountViewModel].
 */
class SecurityViewModel(
    private val security: SecurityRepository,
    private val appLock: AppLockController,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SecurityState(
            appLockEnabled = appLock.settings.enabled.value,
            appLockTimeoutMinutes = appLock.settings.timeoutMinutes.value,
        ),
    )
    val state: StateFlow<SecurityState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = fetch(refreshing = false)

    fun refresh() = fetch(refreshing = true)

    private fun fetch(refreshing: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !refreshing && it.overview == null, isRefreshing = refreshing, loadError = null) }
            when (val result = security.overview()) {
                is DataResult.Success -> _state.update {
                    it.copy(isLoading = false, isRefreshing = false, overview = result.data)
                }
                is DataResult.Failure -> _state.update {
                    // Keep showing what we had; only an empty screen becomes an error screen.
                    it.copy(isLoading = false, isRefreshing = false, loadError = result.error.userMessage())
                }
            }
        }
    }

    /** The screen confirms with Face ID / fingerprint before turning it on, so this only records the choice. */
    fun setAppLockEnabled(enabled: Boolean) {
        appLock.setEnabled(enabled)
        _state.update { it.copy(appLockEnabled = enabled) }
    }

    fun setAppLockTimeout(minutes: Int) {
        appLock.settings.setTimeoutMinutes(minutes)
        _state.update { it.copy(appLockTimeoutMinutes = minutes) }
    }

    fun showDisableDialog(visible: Boolean) = _state.update {
        it.copy(isDisableDialogVisible = visible, disableInput = "", disableError = null, disableWithPassword = false)
    }

    fun toggleDisableMethod() = _state.update {
        it.copy(disableWithPassword = !it.disableWithPassword, disableInput = "", disableError = null)
    }

    fun onDisableInputChange(value: String) = _state.update { it.copy(disableInput = value, disableError = null) }

    fun disableTwoFactor() {
        val current = _state.value
        if (!current.canSubmitDisable) return
        viewModelScope.launch {
            _state.update { it.copy(isDisabling = true, disableError = null) }
            val result = if (current.disableWithPassword) {
                security.disableTwoFactor(code = null, password = current.disableInput)
            } else {
                security.disableTwoFactor(code = current.disableInput.trim(), password = null)
            }
            when (result) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isDisabling = false,
                        isDisableDialogVisible = false,
                        disableInput = "",
                        overview = it.overview?.copy(twoFactorEnabled = false, recoveryCodesRemaining = 0),
                        message = "Two-step verification is off",
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isDisabling = false, disableInput = "", disableError = result.error.userMessage())
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
