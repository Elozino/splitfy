package com.elozino.splitfy.screens.account.security_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.TwoFactorSetupResponse
import com.elozino.splitfy.repositories.SecurityRepository
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TwoFactorSetupState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val setup: TwoFactorSetupResponse? = null,
    val code: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** Non-empty once 2FA is on. Shown this once: the server keeps only hashes. */
    val recoveryCodes: List<String> = emptyList(),
) {
    val isEnabled: Boolean get() = recoveryCodes.isNotEmpty()
    val canConfirm: Boolean get() = !isSubmitting && code.length == 6
}

/**
 * Enrolment: get a secret (QR + key), prove the authenticator has it with one code,
 * then show the recovery codes.
 */
class TwoFactorSetupViewModel(private val security: SecurityRepository) : ViewModel() {

    private val _state = MutableStateFlow(TwoFactorSetupState())
    val state: StateFlow<TwoFactorSetupState> = _state.asStateFlow()

    init {
        start()
    }

    fun start() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            when (val result = security.startTwoFactorSetup()) {
                is DataResult.Success -> _state.update { it.copy(isLoading = false, setup = result.data) }
                is DataResult.Failure -> _state.update { it.copy(isLoading = false, loadError = result.error.userMessage()) }
            }
        }
    }

    fun onCodeChange(value: String) = _state.update {
        it.copy(code = value.filter(Char::isDigit).take(6), errorMessage = null)
    }

    fun confirm() {
        val current = _state.value
        if (!current.canConfirm) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = security.enableTwoFactor(current.code)) {
                is DataResult.Success -> _state.update {
                    it.copy(isSubmitting = false, code = "", recoveryCodes = result.data)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSubmitting = false, code = "", errorMessage = result.error.userMessage())
                }
            }
        }
    }
}
