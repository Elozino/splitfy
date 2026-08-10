package com.elozino.splitify.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.core.domain.DataError
import com.elozino.splitify.core.domain.DataResult
import com.elozino.splitify.core.domain.repository.AuthRepository
import com.elozino.splitify.core.domain.session.SessionManager
import com.elozino.splitify.core.domain.userMessage
import com.elozino.splitify.shared.api.dto.LoginRequest
import com.elozino.splitify.shared.validation.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the whole sign-in / sign-up / password-reset flow.
 *
 * `fieldErrors` is keyed by the same field names the server returns, so inline
 * errors from local validation and from the API render through one code path.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val otpCode: String = "",
    val newPassword: String = "",
    val isSubmitting: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val resetCodeSent: Boolean = false,
    val resetToken: String? = null,
    val passwordReset: Boolean = false,
) {
    val canSubmitLogin: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank()

    val canSubmitSignUp: Boolean
        get() = canSubmitLogin && displayName.isNotBlank()
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, fieldErrors = it.fieldErrors - "email", errorMessage = null)
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, fieldErrors = it.fieldErrors - "password", errorMessage = null)
    }

    fun onDisplayNameChange(value: String) = _state.update {
        it.copy(displayName = value, fieldErrors = it.fieldErrors - "displayName", errorMessage = null)
    }

    fun onOtpChange(value: String) = _state.update { it.copy(otpCode = value, errorMessage = null) }

    fun onNewPasswordChange(value: String) = _state.update {
        it.copy(newPassword = value, fieldErrors = it.fieldErrors - "newPassword", errorMessage = null)
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    /**
     * Validates with the same rules the server enforces (from `:shared`) before
     * spending a round trip on a request that would be rejected anyway.
     */
    fun login() {
        val current = _state.value
        val localErrors = Validators.login(LoginRequest(current.email, current.password))
        if (localErrors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = localErrors.associate { e -> e.field to e.message }) }
            return
        }
        submit { authRepository.login(current.email.trim(), current.password) }
    }

    fun signUp() {
        val current = _state.value
        val localErrors = listOfNotNull(
            Validators.email(current.email),
            Validators.password(current.password),
            Validators.displayName(current.displayName),
        )
        if (localErrors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = localErrors.associate { e -> e.field to e.message }) }
            return
        }
        submit {
            authRepository.register(
                email = current.email.trim(),
                password = current.password,
                displayName = current.displayName.trim(),
            )
        }
    }

    fun requestPasswordReset() {
        val email = _state.value.email
        Validators.email(email)?.let { error ->
            _state.update { it.copy(fieldErrors = mapOf(error.field to error.message)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            when (val result = authRepository.requestPasswordReset(email.trim())) {
                is DataResult.Success ->
                    _state.update { it.copy(isSubmitting = false, resetCodeSent = true) }
                is DataResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun verifyOtp() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            when (val result = authRepository.verifyOtp(current.email.trim(), current.otpCode)) {
                is DataResult.Success ->
                    _state.update { it.copy(isSubmitting = false, resetToken = result.data) }
                is DataResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun resetPassword() {
        val current = _state.value
        val token = current.resetToken ?: return
        Validators.password(current.newPassword, "newPassword")?.let { error ->
            _state.update { it.copy(fieldErrors = mapOf(error.field to error.message)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            when (val result = authRepository.resetPassword(current.email.trim(), token, current.newPassword)) {
                is DataResult.Success ->
                    _state.update { it.copy(isSubmitting = false, passwordReset = true) }
                is DataResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun submit(action: suspend () -> DataResult<*>) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null, fieldErrors = emptyMap()) }
            when (val result = action()) {
                is DataResult.Success -> _state.update {
                    // The password is dropped from state as soon as it is no longer
                    // needed, so it cannot be read back out of a state dump.
                    it.copy(isSubmitting = false, isAuthenticated = true, password = "")
                }
                is DataResult.Failure -> handleFailure(result.error)
            }
        }
    }

    private fun handleFailure(error: DataError) {
        _state.update { current ->
            when (error) {
                is DataError.Validation -> current.copy(
                    isSubmitting = false,
                    fieldErrors = error.fieldErrors.associate { it.field to it.message },
                )
                else -> current.copy(isSubmitting = false, errorMessage = error.userMessage())
            }
        }
    }
}
