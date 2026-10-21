package com.elozino.splitfy.api.dto

import com.elozino.splitfy.domain.model.User
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * What `POST /auth/login` and `POST /auth/oauth/{provider}` return.
 *
 * Without two-step verification this carries [user] and [tokens], so on the wire it
 * is exactly an [AuthResponse]. With it, only [twoFactor] is set: the password (or
 * the Google/Apple token) was right, but nothing is issued until the code is.
 */
@Serializable
data class SignInResponse(
    val user: User? = null,
    val tokens: TokenPair? = null,
    val twoFactor: TwoFactorChallenge? = null,
)

/** Proof that the first factor passed. Exchange it with a code at `POST /auth/2fa/verify`. */
@Serializable
data class TwoFactorChallenge(
    val challengeToken: String,
    val expiresInSeconds: Long,
)

/** [code] is either the authenticator's current 6 digits or one unused recovery code. */
@Serializable
data class TwoFactorVerifyRequest(
    val challengeToken: String,
    val code: String,
)

/** The Security screen's header facts. */
@Serializable
data class SecurityOverview(
    /**
     * False for accounts created through Google or Apple that never chose a password:
     * they can set one without knowing the random one the server generated.
     */
    val passwordSet: Boolean,
    val twoFactorEnabled: Boolean,
    val recoveryCodesRemaining: Int = 0,
)

/** [currentPassword] is required unless [SecurityOverview.passwordSet] is false. */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String? = null,
    val newPassword: String,
)

/** A signed-in device: one chain of rotating refresh tokens. */
@Serializable
data class DeviceSession(
    val id: String,
    /** What the device called itself (e.g. "Pixel 8"); null for sessions from before devices were named. */
    val deviceName: String? = null,
    val platform: DevicePlatform? = null,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    /** The session making this request. It cannot be revoked from the list; sign out instead. */
    val current: Boolean = false,
)

@Serializable
data class RevokedSessionsResponse(
    val revoked: Int,
)

/** Enrolment, step one. The secret is only stored once [TwoFactorEnableRequest] proves the app has it. */
@Serializable
data class TwoFactorSetupResponse(
    /** Base32, for typing into an authenticator by hand. */
    val secret: String,
    /** `otpauth://totp/...`, for the QR code or for opening an authenticator app directly. */
    val otpauthUri: String,
)

@Serializable
data class TwoFactorEnableRequest(
    val code: String,
)

/** Shown once. Only hashes are kept, so they cannot be displayed again. */
@Serializable
data class RecoveryCodesResponse(
    val recoveryCodes: List<String>,
)

/** Either a current code (authenticator or recovery) or the account password. */
@Serializable
data class TwoFactorDisableRequest(
    val code: String? = null,
    val password: String? = null,
)
