package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.RecoveryCodesResponse
import com.elozino.splitfy.api.dto.TwoFactorChallenge
import com.elozino.splitfy.api.dto.TwoFactorDisableRequest
import com.elozino.splitfy.api.dto.TwoFactorSetupResponse
import com.elozino.splitfy.api.dto.TwoFactorVerifyRequest
import com.elozino.splitfy.backend.repositories.StoredChallenge
import com.elozino.splitfy.backend.repositories.TotpRecord
import com.elozino.splitfy.backend.repositories.TwoFactorRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.backend.utils.PasswordHasher
import com.elozino.splitfy.backend.utils.SecretCipher
import com.elozino.splitfy.backend.utils.TokenGenerator
import com.elozino.splitfy.backend.utils.Totp
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Authenticator-app two-step verification (TOTP, RFC 6238).
 *
 * Every code check, whether at sign-in, enrolment or disabling, goes through
 * [checkCode], which enforces one account-wide limit: [MAX_FAILURES] wrong codes lock
 * code entry for [LOCKOUT]. It is per account rather than per IP because six digits
 * is a small space and an attacker who has the password can rotate addresses.
 */
class TwoFactorService(
    private val repository: TwoFactorRepository,
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val cipher: SecretCipher,
    private val clock: Clock = Clock.System,
) {

    private val random = SecureRandom()

    suspend fun isEnabled(userId: UUID): Boolean = repository.find(userId)?.isEnabled == true

    suspend fun recoveryCodesRemaining(userId: UUID): Int = repository.remainingRecoveryCodes(userId)

    /** A new secret for the authenticator app. Nothing changes for sign-in until [enable]. */
    suspend fun setup(userId: UUID, email: String): TwoFactorSetupResponse {
        if (isEnabled(userId)) {
            throw AppException.Conflict("Two-step verification is already on. Turn it off first to use a new app.")
        }
        // An unverified account can be taken over by whoever proves the address with
        // Google or Apple (see SocialAuthService). Letting a squatter attach their
        // authenticator first would lock the real owner out after that takeover.
        if (users.findById(userId)?.emailVerified != true) {
            throw AppException.Forbidden("Verify your email address before turning on two-step verification")
        }
        val secret = Totp.newSecret()
        repository.savePending(userId, cipher.encrypt(secret))
        return TwoFactorSetupResponse(
            secret = com.elozino.splitfy.backend.utils.Base32.encode(secret),
            otpauthUri = Totp.otpauthUri(ISSUER, email, secret),
        )
    }

    /** Proves the app holds the secret, then turns 2FA on and hands out recovery codes, once. */
    suspend fun enable(userId: UUID, code: String): RecoveryCodesResponse {
        val record = repository.find(userId)
            ?: throw AppException.BadRequest("Start setup first", ApiErrorCode.CONFLICT)
        if (record.isEnabled) throw AppException.Conflict("Two-step verification is already on")

        if (!checkCode(userId, record, code, allowRecoveryCode = false)) {
            throw wrongCode()
        }
        val codes = List(RECOVERY_CODE_COUNT) { newRecoveryCode() }
        repository.enable(userId, codes.map { TokenGenerator.sha256(normalizeRecoveryCode(it)) })
        return RecoveryCodesResponse(codes)
    }

    /** Needs a current code (authenticator or recovery) or the password, so a borrowed unlocked phone is not enough. */
    suspend fun disable(userId: UUID, request: TwoFactorDisableRequest) {
        val record = repository.find(userId)?.takeIf { it.isEnabled } ?: return

        val code = request.code?.takeIf { it.isNotBlank() }
        val password = request.password?.takeIf { it.isNotEmpty() }
        val proven = when {
            code != null -> checkCode(userId, record, code, allowRecoveryCode = true)
            password != null -> {
                ensureNotLocked(record)
                val account = users.findRecordById(userId) ?: throw AppException.Unauthorized()
                (account.hasPassword && hasher.verify(password, account.passwordHash)).also { ok ->
                    if (ok) repository.resetFailures(userId) else repository.recordFailure(userId)
                }
            }
            else -> throw AppException.Validation(
                listOf(com.elozino.splitfy.api.FieldError("code", "Enter a code from your authenticator app or your password")),
            )
        }
        if (!proven) {
            throw if (code != null) wrongCode() else AppException.BadRequest(
                "Password is incorrect",
                ApiErrorCode.INVALID_CREDENTIALS,
            )
        }
        repository.disable(userId)
    }

    /** The first factor passed; the caller gets this instead of tokens. */
    suspend fun createChallenge(userId: UUID, deviceId: String?): TwoFactorChallenge {
        val token = TokenGenerator.opaqueToken()
        repository.createChallenge(
            userId = userId,
            tokenHash = TokenGenerator.sha256(token),
            deviceId = deviceId,
            expiresAt = clock.now() + CHALLENGE_TTL,
        )
        return TwoFactorChallenge(token, CHALLENGE_TTL.inWholeSeconds)
    }

    /**
     * Second sign-in step. Returns the challenge once the code is right; every
     * failure is an [AppException.InvalidCredentials] (a 401 on an unauthenticated
     * endpoint), with a message that says whether to retry or to sign in again.
     */
    suspend fun redeem(request: TwoFactorVerifyRequest): StoredChallenge {
        val challenge = repository.findChallenge(TokenGenerator.sha256(request.challengeToken))
            ?.takeIf { it.consumedAt == null && it.expiresAt > clock.now() && it.attempts < MAX_CHALLENGE_ATTEMPTS }
            ?: throw AppException.InvalidCredentials("This sign-in has expired. Please sign in again.")

        val record = repository.find(challenge.userId)?.takeIf { it.isEnabled }
        if (record == null) {
            // Turned off since the challenge was issued: the first factor is all there is.
            if (!repository.consumeChallenge(challenge.id)) throw expiredChallenge()
            return challenge
        }

        if (!checkCode(challenge.userId, record, request.code, allowRecoveryCode = true)) {
            repository.recordChallengeFailure(challenge.id)
            throw AppException.InvalidCredentials("That code isn't right. Check your authenticator app and try again.")
        }
        if (!repository.consumeChallenge(challenge.id)) throw expiredChallenge()
        return challenge
    }

    /**
     * True if [rawCode] is the current authenticator code (and has not been used
     * before) or, when allowed, an unused recovery code, which is then spent.
     */
    private suspend fun checkCode(userId: UUID, record: TotpRecord, rawCode: String, allowRecoveryCode: Boolean): Boolean {
        ensureNotLocked(record)

        val code = rawCode.filterNot { it.isWhitespace() }
        val ok = if (code.length == Totp.DIGITS && code.all(Char::isDigit)) {
            val secret = cipher.decrypt(record.secretEncrypted)
            val step = Totp.matchingStep(secret, code, clock.now().epochSeconds, afterStep = record.lastUsedStep)
            step != null && repository.acceptStep(userId, step)
        } else {
            allowRecoveryCode && repository.useRecoveryCode(userId, TokenGenerator.sha256(normalizeRecoveryCode(code)))
                .also { used -> if (used) repository.resetFailures(userId) }
        }
        if (!ok) repository.recordFailure(userId)
        return ok
    }

    private fun ensureNotLocked(record: TotpRecord) {
        val lastFailure = record.lastFailedAt ?: return
        if (record.failedAttempts >= MAX_FAILURES && clock.now() - lastFailure < LOCKOUT) {
            throw AppException.TooManyRequests("Too many incorrect codes. Try again in 15 minutes.")
        }
    }

    /** 400, not 401: on authenticated endpoints a 401 would make the client refresh and retry. */
    private fun wrongCode() = AppException.BadRequest(
        "That code isn't right. Check your authenticator app and try again.",
        ApiErrorCode.INVALID_CREDENTIALS,
    )

    private fun expiredChallenge() = AppException.InvalidCredentials("This sign-in has expired. Please sign in again.")

    /** Ten characters from an alphabet without look-alikes, shown as `xxxxx-xxxxx`. ~50 bits each. */
    private fun newRecoveryCode(): String {
        val chars = List(RECOVERY_CODE_LENGTH) { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }
        return chars.take(5).joinToString("") + "-" + chars.drop(5).joinToString("")
    }

    private fun normalizeRecoveryCode(code: String): String =
        code.lowercase().filter { it.isLetterOrDigit() }

    companion object {
        const val ISSUER = "splitfy"
        const val MAX_FAILURES = 5
        const val MAX_CHALLENGE_ATTEMPTS = 5
        const val RECOVERY_CODE_COUNT = 10
        private const val RECOVERY_CODE_LENGTH = 10
        private const val RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
        val LOCKOUT = 15.minutes
        val CHALLENGE_TTL = 5.minutes
    }
}
