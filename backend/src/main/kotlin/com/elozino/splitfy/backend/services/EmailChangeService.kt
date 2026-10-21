package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.EmailChangeConfirmRequest
import com.elozino.splitfy.api.dto.EmailChangePending
import com.elozino.splitfy.api.dto.EmailChangeRequest
import com.elozino.splitfy.backend.repositories.EmailChangeRepository
import com.elozino.splitfy.backend.repositories.RefreshTokenRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.backend.utils.PasswordHasher
import com.elozino.splitfy.backend.utils.TokenGenerator
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.validation.Validators
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.slf4j.LoggerFactory

/**
 * Moving an account to a new email address.
 *
 * The code goes to the *new* address, so confirming it proves the user owns that
 * address; the account is then verified at once. The old address is told about the
 * change, which is what catches a hijacked session quietly taking the account.
 *
 * Who may hold an address follows [SocialAuthService]'s rule: a verified owner keeps
 * it, but an unverified claim never blocks someone who proves ownership. If another
 * account registered the address without ever verifying it, that account loses it
 * when this change is confirmed (its password and sessions are revoked, since whoever
 * set them never proved who they were).
 */
class EmailChangeService(
    private val users: UserRepository,
    private val changes: EmailChangeRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val hasher: PasswordHasher,
    private val notifier: OtpNotifier,
    private val clock: Clock = Clock.System,
) {

    private val logger = LoggerFactory.getLogger(EmailChangeService::class.java)

    suspend fun request(userId: UUID, request: EmailChangeRequest): EmailChangePending {
        Validators.email(request.newEmail, "newEmail")?.let { throw AppException.Validation(listOf(it)) }
        val newEmail = request.newEmail.trim().lowercase()
        val user = users.findById(userId) ?: throw AppException.Unauthorized()

        if (newEmail == user.email) throw AppException.BadRequest("That's already your email")
        requireAvailable(newEmail, userId)

        val now = clock.now()
        changes.latestIssuedAt(userId)?.let { last ->
            if (now - last < COOLDOWN) {
                throw AppException.TooManyRequests("Wait a minute before asking for another code")
            }
        }
        if (changes.countIssuedSince(userId, now - DAILY_WINDOW) >= MAX_PER_DAY) {
            throw AppException.TooManyRequests("You've changed your email too many times today. Try again tomorrow.")
        }

        val code = TokenGenerator.numericOtp()
        val expiresAt = now + CODE_TTL
        changes.createChange(userId, newEmail, TokenGenerator.sha256(code), expiresAt)
        // Unlike sign-up, the user is waiting for this code and can do nothing without it.
        try {
            notifier.sendEmailChangeCode(newEmail, code)
        } catch (failure: Exception) {
            logger.error("Could not send email change code", failure)
            changes.cancelPending(userId)
            throw AppException.Internal("We couldn't send the code. Try again in a moment.", failure)
        }
        return EmailChangePending(newEmail, expiresAt)
    }

    suspend fun confirm(userId: UUID, request: EmailChangeConfirmRequest): User {
        val user = users.findById(userId) ?: throw AppException.Unauthorized()
        val now = clock.now()
        val change = changes.findActiveChange(userId, TokenGenerator.sha256(request.code.trim()), now)
            ?: run {
                changes.recordFailedAttempt(userId, now)
                // 400, not 401: see AuthService.verifyEmail.
                throw AppException.BadRequest("Code is not valid or has expired", ApiErrorCode.INVALID_CREDENTIALS)
            }

        // The address may have been taken, or verified by its holder, since the code went out.
        requireAvailable(change.newEmail, userId, releaseSquatter = true)
        changes.consume(change.id)
        try {
            users.changeEmail(userId, change.newEmail)
        } catch (race: ExposedSQLException) {
            throw emailTaken()
        }

        runCatching { notifier.sendEmailChangedNotice(user.email, change.newEmail) }
            .onFailure { logger.error("Could not send email changed notice", it) }
        return users.findById(userId) ?: throw AppException.Unauthorized()
    }

    /** Throws unless [email] is free for [userId]; with [releaseSquatter], frees it from an unverified holder. */
    private suspend fun requireAvailable(email: String, userId: UUID, releaseSquatter: Boolean = false) {
        val holder = users.findByEmail(email)?.user ?: return
        val holderId = UUID.fromString(holder.id.value)
        if (holderId == userId) return
        if (holder.emailVerified) throw emailTaken()
        if (!releaseSquatter) return

        if (!users.releaseUnverifiedEmail(holderId, hasher.hash(TokenGenerator.opaqueToken()))) throw emailTaken()
        refreshTokens.revokeAllForUser(holderId)
        logger.warn("Unverified account {} lost its address to a verified email change by {}", holderId, userId)
    }

    private fun emailTaken() =
        AppException.Conflict("Another account already uses that email", ApiErrorCode.EMAIL_ALREADY_REGISTERED)

    private companion object {
        val CODE_TTL = 30.minutes
        val COOLDOWN = 60.seconds
        val DAILY_WINDOW = 24.hours
        const val MAX_PER_DAY = 5
    }
}
