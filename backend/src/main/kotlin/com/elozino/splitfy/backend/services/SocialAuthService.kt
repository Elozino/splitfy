package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.LinkedAccountsResponse
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.backend.repositories.RefreshTokenRepository
import com.elozino.splitfy.backend.repositories.UserIdentityRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.backend.utils.PasswordHasher
import com.elozino.splitfy.backend.utils.TokenGenerator
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.User
import java.util.UUID
import org.slf4j.LoggerFactory

/**
 * Sign in with Google or Apple.
 *
 * Resolution order, by what the provider has proven:
 *  1. The identity (provider + subject) is already linked: sign into that account.
 *  2. An account with the same email exists and its owner verified it: link and sign in.
 *  3. An account with the same email exists but was never verified: the provider has
 *     just proven who owns the address, so they take the account over. The old
 *     password and sessions are revoked, since whoever set them never proved it.
 *  4. Otherwise create a verified account.
 *
 * Signed-in users can also link and unlink identities from Linked Accounts. An
 * identity belongs to one account only, and an account always keeps at least one way
 * to sign in: a password its owner knows (`users.has_password`) or an identity.
 */
class SocialAuthService(
    private val users: UserRepository,
    private val identities: UserIdentityRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val hasher: PasswordHasher,
    private val auth: AuthService,
    private val notifications: NotificationService,
    verifiers: List<IdentityTokenVerifier>,
) {

    private val logger = LoggerFactory.getLogger(SocialAuthService::class.java)
    private val verifiers = verifiers.associateBy { it.provider }

    suspend fun signIn(provider: OAuthProvider, request: OAuthSignInRequest): AuthResponse {
        val identity = verify(provider, request)
            ?: throw AppException.InvalidCredentials("We couldn't confirm your ${provider.label} sign-in. Please try again.")

        identities.findUserId(provider, identity.subject)?.let { userId ->
            val user = users.findById(userId) ?: throw AppException.Unauthorized()
            return auth.startSession(user)
        }

        val email = identity.email?.takeIf { identity.emailVerified }
            ?: throw AppException.BadRequest("${provider.label} didn't share a verified email address")

        val existing = users.findByEmail(email)?.user
        val user = when {
            existing == null -> createAccount(email, request.displayName ?: identity.name)
            existing.emailVerified -> existing
            else -> takeOver(existing)
        }

        identities.link(provider, identity.subject, UUID.fromString(user.id.value), email)
        return auth.startSession(user)
    }

    suspend fun linkedAccounts(userId: UUID): LinkedAccountsResponse = LinkedAccountsResponse(
        identities = identities.listForUser(userId),
        hasPassword = users.hasPassword(userId),
        availableProviders = OAuthProvider.entries.filter { verifiers[it]?.isConfigured == true },
    )

    /**
     * Adds the identity in [request] to the signed-in account. Its email need not match
     * the account's: the provider has proven who signed in, which is all linking needs.
     */
    suspend fun link(userId: UUID, provider: OAuthProvider, request: OAuthSignInRequest): LinkedAccountsResponse {
        val identity = verify(provider, request)
            // 400, not 401: the caller is signed in, and a 401 would make the app refresh and retry.
            ?: throw AppException.BadRequest(
                "We couldn't confirm your ${provider.label} sign-in. Please try again.",
                ApiErrorCode.INVALID_CREDENTIALS,
            )

        when (identities.findUserId(provider, identity.subject)) {
            userId -> return linkedAccounts(userId)
            null -> Unit
            else -> throw AppException.Conflict("That ${provider.label} account is already linked to another splitfy account")
        }
        if (identities.listForUser(userId).any { it.provider == provider }) {
            throw AppException.Conflict("Unlink your current ${provider.label} account first")
        }

        identities.link(provider, identity.subject, userId, identity.email?.takeIf { identity.emailVerified })
        // Lost a race with another account linking the same identity: insertIgnore kept theirs.
        if (identities.findUserId(provider, identity.subject) != userId) {
            throw AppException.Conflict("That ${provider.label} account is already linked to another splitfy account")
        }
        return linkedAccounts(userId)
    }

    /** Refuses to remove the account's last way to sign in. */
    suspend fun unlink(userId: UUID, provider: OAuthProvider): LinkedAccountsResponse {
        val linked = identities.listForUser(userId)
        if (linked.none { it.provider == provider }) throw AppException.NotFound("${provider.label} account")
        if (!users.hasPassword(userId) && linked.none { it.provider != provider }) {
            throw AppException.Conflict(
                "${provider.label} is the only way into this account. Set a password with \"Forgot password\" " +
                    "on the sign-in screen, or link another account, before unlinking it.",
            )
        }
        identities.unlink(userId, provider)
        return linkedAccounts(userId)
    }

    private fun verify(provider: OAuthProvider, request: OAuthSignInRequest): VerifiedIdentity? {
        val verifier = verifiers[provider]?.takeIf { it.isConfigured }
            ?: throw AppException.NotFound("Sign in with ${provider.label} is not available")
        return verifier.verify(request.idToken, request.nonce)
    }

    private suspend fun createAccount(email: String, name: String?): User {
        val user = users.create(
            email = email,
            passwordHash = unusablePasswordHash(),
            displayName = displayNameFrom(name, email),
            phoneNumber = null,
            defaultCurrency = CurrencyCode.NGN,
            emailVerified = true,
            hasPassword = false,
        )
        notifications.welcome(UUID.fromString(user.id.value), user.displayName)
        return user
    }

    private suspend fun takeOver(account: User): User {
        val id = UUID.fromString(account.id.value)
        logger.warn("Unverified account {} claimed by a provider-verified sign-in; revoking its password", id)
        users.updatePasswordHash(id, unusablePasswordHash(), usable = false)
        refreshTokens.revokeAllForUser(id)
        users.markEmailVerified(id)
        return users.findById(id) ?: throw AppException.Unauthorized()
    }

    /** A hash nobody knows the input to. The user can set a real one through password reset. */
    private fun unusablePasswordHash(): String = hasher.hash(TokenGenerator.opaqueToken())

    private fun displayNameFrom(name: String?, email: String): String =
        name?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH)
            ?: email.substringBefore('@').take(MAX_NAME_LENGTH)


    private companion object {
        const val MAX_NAME_LENGTH = 60
    }
}

internal val OAuthProvider.label: String
    get() = when (this) {
        OAuthProvider.GOOGLE -> "Google"
        OAuthProvider.APPLE -> "Apple"
    }
