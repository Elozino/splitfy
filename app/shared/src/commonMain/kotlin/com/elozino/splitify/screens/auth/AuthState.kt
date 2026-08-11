package com.elozino.splitify.screens

/** Shared state for sign-in, sign-up, and password recovery. */
data class AuthState(
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
