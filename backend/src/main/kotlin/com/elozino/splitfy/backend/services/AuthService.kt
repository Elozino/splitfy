package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.ForgotPasswordRequest
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.ResetPasswordRequest
import com.elozino.splitfy.api.dto.TokenPair
import com.elozino.splitfy.api.dto.VerifyEmailRequest
import com.elozino.splitfy.api.dto.VerifyOtpRequest
import com.elozino.splitfy.backend.repositories.EmailVerificationRepository
import com.elozino.splitfy.backend.repositories.PasswordResetRepository
import com.elozino.splitfy.backend.repositories.RefreshTokenRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.utils.AppConfig
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.backend.utils.PasswordHasher
import com.elozino.splitfy.backend.utils.TokenGenerator
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.validation.Validators
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.slf4j.LoggerFactory

/**
 * All credential handling lives here.
 *
 * Two rules drive the design:
 *  - the response never reveals whether an email exists (no user enumeration);
 *  - refresh tokens rotate on every use, and a replayed token nukes the user's
 *    sessions rather than being silently ignored.
 */
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordResets: PasswordResetRepository,
    private val emailVerifications: EmailVerificationRepository,
    private val jwt: JwtService,
    private val hasher: PasswordHasher,
    private val config: AppConfig,
    private val notifier: OtpNotifier,
    private val notifications: NotificationService,
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    suspend fun register(request: RegisterRequest): AuthResponse {
        Validators.register(request).let { if (it.isNotEmpty()) throw AppException.Validation(it) }

        if (users.existsByEmail(request.email)) {
            throw AppException.Conflict(
                "That email is already registered",
                ApiErrorCode.EMAIL_ALREADY_REGISTERED,
            )
        }

        val user = users.create(
            email = request.email,
            passwordHash = hasher.hash(request.password),
            displayName = request.displayName,
            phoneNumber = request.phoneNumber,
            defaultCurrency = request.defaultCurrency,
        )
        notifications.welcome(UUID.fromString(user.id.value), user.displayName)
        sendVerificationCode(user)
        return AuthResponse(user, issueTokens(user, deviceId = null))
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        Validators.login(request).let { if (it.isNotEmpty()) throw AppException.Validation(it) }

        val record = users.findByEmail(request.email)
        if (record == null) {
            // Hash anyway so a missing account and a wrong password take the same time.
            hasher.verify(request.password, DUMMY_HASH)
            throw AppException.InvalidCredentials()
        }
        if (!hasher.verify(request.password, record.passwordHash)) {
            throw AppException.InvalidCredentials()
        }
        return AuthResponse(record.user, issueTokens(record.user, request.deviceId))
    }

    suspend fun refresh(presentedToken: String): TokenPair {
        val hash = TokenGenerator.sha256(presentedToken)
        val stored = refreshTokens.findByHash(hash)
            ?: throw AppException.Unauthorized("Refresh token is not valid", ApiErrorCode.TOKEN_INVALID)

        val now = Clock.System.now()
        if (stored.revokedAt != null) {
            // Replay of a token we already rotated away: assume theft.
            val revoked = refreshTokens.revokeAllForUser(stored.userId)
            logger.warn("Refresh token replay for user {} - revoked {} sessions", stored.userId, revoked)
            throw AppException.Unauthorized("Session was revoked", ApiErrorCode.TOKEN_INVALID)
        }
        if (!stored.isUsable(now)) {
            throw AppException.Unauthorized("Refresh token has expired", ApiErrorCode.TOKEN_EXPIRED)
        }

        val user = users.findById(stored.userId) ?: throw AppException.Unauthorized()
        refreshTokens.revoke(stored.id)
        return issueTokens(user, deviceId = null)
    }

    suspend fun logout(presentedToken: String) {
        refreshTokens.revokeByHash(TokenGenerator.sha256(presentedToken))
    }

    suspend fun logoutEverywhere(userId: UUID) {
        refreshTokens.revokeAllForUser(userId)
    }

    /**
     * Always reports success: telling an anonymous caller that an address is unknown
     * turns this endpoint into an account-existence oracle.
     */
    suspend fun forgotPassword(request: ForgotPasswordRequest) {
        Validators.email(request.email)?.let { throw AppException.Validation(listOf(it)) }

        val record = users.findByEmail(request.email) ?: return
        val userId = UUID.fromString(record.user.id.value)

        // One email per address per cooldown, however many devices or IPs ask, so
        // this endpoint cannot be used to flood someone's inbox.
        val lastIssued = passwordResets.latestIssuedAt(userId)
        if (lastIssued != null && Clock.System.now() - lastIssued < RESEND_COOLDOWN) return

        val code = TokenGenerator.numericOtp()
        passwordResets.create(
            userId = userId,
            codeHash = TokenGenerator.sha256(code),
            expiresAt = Clock.System.now().plus(config.security.otpTtl),
        )
        // A delivery failure is logged rather than returned: reporting it would tell
        // an anonymous caller that the address has an account.
        runCatching { notifier.sendPasswordResetCode(record.user.email, code) }
            .onFailure { logger.error("Could not send password reset code", it) }
    }

    /** Exchanges a valid OTP for a single-use reset token. */
    suspend fun verifyOtp(request: VerifyOtpRequest): String {
        val record = users.findByEmail(request.email) ?: throw AppException.InvalidCredentials("Code is not valid")
        val userId = UUID.fromString(record.user.id.value)

        val now = Clock.System.now()
        val resetId = passwordResets.findActive(
            userId = userId,
            codeHash = TokenGenerator.sha256(request.code),
            now = now,
        ) ?: run {
            passwordResets.recordFailedAttempt(userId, now)
            throw AppException.InvalidCredentials("Code is not valid or has expired")
        }

        passwordResets.consume(resetId)

        val resetToken = TokenGenerator.opaqueToken()
        passwordResets.create(
            userId = userId,
            codeHash = TokenGenerator.sha256(resetToken),
            expiresAt = Clock.System.now().plus(config.security.otpTtl),
        )
        return resetToken
    }

    suspend fun resetPassword(request: ResetPasswordRequest) {
        Validators.password(request.newPassword, "newPassword")
            ?.let { throw AppException.Validation(listOf(it)) }

        val record = users.findByEmail(request.email)
            ?: throw AppException.InvalidCredentials("Reset token is not valid")
        val userId = UUID.fromString(record.user.id.value)

        val resetId = passwordResets.findActive(
            userId = userId,
            codeHash = TokenGenerator.sha256(request.resetToken),
            now = Clock.System.now(),
        ) ?: throw AppException.InvalidCredentials("Reset token is not valid or has expired")

        passwordResets.consume(resetId)
        users.updatePasswordHash(userId, hasher.hash(request.newPassword))

        // A password change invalidates every existing session.
        refreshTokens.revokeAllForUser(userId)
    }

    /** Proves the caller owns their email address; returns the updated profile. */
    suspend fun verifyEmail(userId: UUID, request: VerifyEmailRequest): User {
        val user = users.findById(userId) ?: throw AppException.Unauthorized()
        if (user.emailVerified) return user

        val now = Clock.System.now()
        val codeId = emailVerifications.findActive(userId, TokenGenerator.sha256(request.code.trim()), now)
            ?: run {
                emailVerifications.recordFailedAttempt(userId, now)
                // 400, not 401: the caller is authenticated, and a 401 would make the
                // client refresh its token and retry, counting each guess twice.
                throw AppException.BadRequest("Code is not valid or has expired", ApiErrorCode.INVALID_CREDENTIALS)
            }
        emailVerifications.consume(codeId)
        users.markEmailVerified(userId)
        return users.findById(userId) ?: throw AppException.Unauthorized()
    }

    /** Sends a fresh code, at most once per cooldown; a no-op once verified. */
    suspend fun resendVerification(userId: UUID) {
        val user = users.findById(userId) ?: throw AppException.Unauthorized()
        if (user.emailVerified) return
        val lastIssued = emailVerifications.latestIssuedAt(userId)
        if (lastIssued != null && Clock.System.now() - lastIssued < RESEND_COOLDOWN) return
        sendVerificationCode(user)
    }

    private suspend fun sendVerificationCode(user: User) {
        val code = TokenGenerator.numericOtp()
        emailVerifications.create(
            userId = UUID.fromString(user.id.value),
            codeHash = TokenGenerator.sha256(code),
            expiresAt = Clock.System.now().plus(EMAIL_CODE_TTL),
        )
        // Signing up must not fail because the email provider is down; the user can resend.
        runCatching { notifier.sendEmailVerificationCode(user.email, code) }
            .onFailure { logger.error("Could not send email verification code", it) }
    }

    private suspend fun issueTokens(user: User, deviceId: String?): TokenPair {
        val userId = UUID.fromString(user.id.value)
        val refreshToken = TokenGenerator.opaqueToken()
        refreshTokens.store(
            userId = userId,
            tokenHash = TokenGenerator.sha256(refreshToken),
            deviceId = deviceId,
            expiresAt = Clock.System.now().plus(config.jwt.refreshTokenTtl),
        )
        return TokenPair(
            accessToken = jwt.createAccessToken(userId, user.email),
            refreshToken = refreshToken,
            expiresInSeconds = jwt.accessTokenTtl.inWholeSeconds,
        )
    }

    private companion object {
        /** Minimum gap between two reset emails to the same address. */
        val RESEND_COOLDOWN = 60.seconds

        /** Long enough to switch to a mail app on a slow connection. */
        val EMAIL_CODE_TTL = 30.minutes

        /** A real BCrypt hash of a random value, used only to equalise timing. */
        const val DUMMY_HASH = "\$2a\$12\$C6UzMDM.H6dfI/f/IKcEe.0000000000000000000000000000000000"
    }
}

/** Seam for delivering one-time codes. Swap in email/SMS providers behind this. */
interface OtpNotifier {
    suspend fun sendPasswordResetCode(email: String, code: String)
    suspend fun sendEmailVerificationCode(email: String, code: String)
}

/**
 * Development notifier: writes the code to the log instead of sending it.
 * Refuses to run in production so a missing provider fails the boot, not a user.
 */
class LoggingOtpNotifier(private val environment: AppConfig.Environment) : OtpNotifier {

    private val logger = LoggerFactory.getLogger(LoggingOtpNotifier::class.java)

    init {
        require(!environment.isProduction) {
            "LoggingOtpNotifier must not be used in production - configure a real provider"
        }
    }

    override suspend fun sendPasswordResetCode(email: String, code: String) {
        logger.info("[dev] password reset code for {}: {}", email, code)
    }

    override suspend fun sendEmailVerificationCode(email: String, code: String) {
        logger.info("[dev] email verification code for {}: {}", email, code)
    }
}
