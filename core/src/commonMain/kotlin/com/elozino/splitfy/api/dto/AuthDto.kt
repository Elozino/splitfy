package com.elozino.splitfy.api.dto

import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.User
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
/** Path segment of `/auth/oauth/{provider}`. */
@Serializable
enum class OAuthProvider(val path: String) { GOOGLE("google"), APPLE("apple") }

/**
 * Sign in with an ID token from Google or Apple.
 *
 * [nonce] is the value the client asked the provider to embed (for Apple, the
 * SHA-256 of the raw nonce); when present it must match the token. Apple only shares
 * the user's name with the app on the very first sign-in, so it arrives here in
 * [displayName] rather than in the token.
 */
@Serializable
data class OAuthSignInRequest(
    val idToken: String,
    val nonce: String? = null,
    val displayName: String? = null,
)

@Serializable
data class VerifyEmailRequest(
    val code: String,
)

@Serializable
data class VerifyOtpResponse(
    val resetToken: String,
)

/** Endpoints that only need to say "done" return this rather than an empty body. */
@Serializable
data class MessageResponse(
    val message: String,
)
