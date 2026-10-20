package com.elozino.splitfy.screens.auth.shared

import com.elozino.splitfy.SessionState
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.social.SocialSignInResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.repositories.AuthRepository
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.userMessage
import com.elozino.splitfy.validation.Validators
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        // This view model outlives a session: one instance serves every auth screen.
        // Start clean after a sign-out, or the auth screens would still see
        // `isAuthenticated` from the previous session and jump straight to Home.
        viewModelScope.launch {
            sessionManager.state.collect { session ->
                if (session is SessionState.SignedOut) _state.value = AuthState()
            }
        }
    }

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

    fun toggleLoginPasswordVisibility() = _state.update {
        it.copy(isLoginPasswordVisible = !it.isLoginPasswordVisible)
    }

    fun onRememberLoginChange(value: Boolean) = _state.update { it.copy(rememberLogin = value) }

    fun toggleSignUpPasswordVisibility() = _state.update {
        it.copy(isSignUpPasswordVisible = !it.isSignUpPasswordVisible)
    }

    fun onConfirmPasswordChange(value: String) = _state.update { it.copy(confirmPassword = value) }

    fun toggleNewPasswordVisibility() = _state.update {
        it.copy(isNewPasswordVisible = !it.isNewPasswordVisible)
    }

    fun toggleConfirmPasswordVisibility() = _state.update {
        it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible)
    }

    fun tickOtpTimer() = _state.update {
        it.copy(otpSecondsRemaining = (it.otpSecondsRemaining - 1).coerceAtLeast(0))
    }

    fun resetOtpTimer() = _state.update { it.copy(otpSecondsRemaining = 55) }

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
                is DataResult.Failure -> {
                    handleFailure(result.error)
                    _state.update { it.copy(otpCode = "") }
                }
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

    /** Submits the sign-up code; a wrong one is cleared so the user can type again. */
    fun verifyEmail() {
        val code = _state.value.otpCode
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = authRepository.verifyEmail(code)) {
                is DataResult.Success ->
                    _state.update { it.copy(isSubmitting = false, emailVerified = true, otpCode = "") }
                is DataResult.Failure -> {
                    handleFailure(result.error)
                    _state.update { it.copy(otpCode = "") }
                }
            }
        }
    }

    fun resendVerification() {
        viewModelScope.launch {
            when (val result = authRepository.resendVerification()) {
                is DataResult.Success -> _state.update { it.copy(errorMessage = null) }
                is DataResult.Failure -> handleFailure(result.error)
            }
        }
    }

    /** Hands a provider's ID token to the server; a cancelled sheet is not an error. */
    fun onSocialSignIn(provider: OAuthProvider, result: SocialSignInResult) {
        when (result) {
            SocialSignInResult.Cancelled -> Unit
            is SocialSignInResult.Failure -> _state.update { it.copy(errorMessage = result.message) }
            is SocialSignInResult.Success -> submit {
                authRepository.signInWithProvider(
                    provider,
                    OAuthSignInRequest(result.idToken, result.nonce, result.displayName),
                )
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

