package com.elozino.splitify.api.dto

import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.User
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val phoneNumber: String? = null,
    val defaultCurrency: CurrencyCode = CurrencyCode.NGN,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    /** Identifies the device so a single logout does not kill every session. */
    val deviceId: String? = null,
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

@Serializable
data class VerifyOtpRequest(
    val email: String,
    val code: String,
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val resetToken: String,
    val newPassword: String,
)

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    /** Access-token lifetime in seconds; the client refreshes slightly before this. */
    val expiresInSeconds: Long,
    val tokenType: String = "Bearer",
)

@Serializable
data class AuthResponse(
    val user: User,
    val tokens: TokenPair,
)

/** Single-use token handed out once an OTP has been verified. */
@Serializable
data class VerifyOtpResponse(
    val resetToken: String,
)

/** Endpoints that only need to say "done" return this rather than an empty body. */
@Serializable
data class MessageResponse(
    val message: String,
)
