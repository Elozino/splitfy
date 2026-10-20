package com.elozino.splitfy.backend.services

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.elozino.splitfy.api.dto.OAuthProvider
import java.net.URI
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

/** What a provider vouches for once its ID token has been checked. */
data class VerifiedIdentity(
    val provider: OAuthProvider,
    /** Stable, provider-scoped user id (`sub`). Never reused, unlike an email. */
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
)

/**
 * Checks an OpenID Connect ID token: RS256 signature against the provider's
 * published keys, issuer, audience (one of our client ids), expiry, and the nonce
 * when the client supplied one. Anything else is rejected.
 */
class IdentityTokenVerifier(
    val provider: OAuthProvider,
    private val keys: JwkProvider,
    private val issuers: Set<String>,
    private val audiences: Set<String>,
) {

    val isConfigured: Boolean get() = audiences.isNotEmpty()

    fun verify(idToken: String, expectedNonce: String?): VerifiedIdentity? {
        if (!isConfigured) return null
        return try {
            val algorithm = Algorithm.RSA256(object : RSAKeyProvider {
                override fun getPublicKeyById(keyId: String?): RSAPublicKey = keys.get(keyId).publicKey as RSAPublicKey
                override fun getPrivateKey(): RSAPrivateKey? = null
                override fun getPrivateKeyId(): String? = null
            })
            val decoded = JWT.require(algorithm)
                .withIssuer(*issuers.toTypedArray())
                .withAnyOfAudience(*audiences.toTypedArray())
                .acceptLeeway(CLOCK_SKEW_SECONDS)
                .build()
                .verify(idToken)

            if (expectedNonce != null && decoded.getClaim("nonce").asString() != expectedNonce) return null

            // Google sends a boolean; Apple has sent both a boolean and the string "true".
            val verifiedClaim = decoded.getClaim("email_verified")
            val emailVerified = verifiedClaim.asBoolean() ?: (verifiedClaim.asString() == "true")

            VerifiedIdentity(
                provider = provider,
                subject = decoded.subject ?: return null,
                email = decoded.getClaim("email").asString()?.trim()?.lowercase(),
                emailVerified = emailVerified,
                name = decoded.getClaim("name").asString(),
            )
        } catch (_: JWTVerificationException) {
            null
        } catch (_: com.auth0.jwk.JwkException) {
            null
        }
    }

    companion object {
        private const val CLOCK_SKEW_SECONDS = 60L

        fun google(clientIds: Set<String>) = IdentityTokenVerifier(
            provider = OAuthProvider.GOOGLE,
            keys = cachedKeys("https://www.googleapis.com/oauth2/v3/certs"),
            issuers = setOf("https://accounts.google.com", "accounts.google.com"),
            audiences = clientIds,
        )

        fun apple(clientIds: Set<String>) = IdentityTokenVerifier(
            provider = OAuthProvider.APPLE,
            keys = cachedKeys("https://appleid.apple.com/auth/keys"),
            issuers = setOf("https://appleid.apple.com"),
            audiences = clientIds,
        )

        /** Keys rotate rarely; cache them and refetch only for an unknown key id. */
        private fun cachedKeys(url: String): JwkProvider = JwkProviderBuilder(URI(url).toURL())
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
    }
}
