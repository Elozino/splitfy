package com.elozino.splitfy.backend.services

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.elozino.splitfy.backend.repositories.RefreshTokenRepository
import com.elozino.splitfy.backend.utils.AppConfig
import java.util.Date
import java.util.UUID
import kotlin.time.Duration

/**
 * Issues and verifies stateless access tokens.
 *
 * Access tokens are short-lived and never revoked individually — revocation is the
 * refresh token's job (see [RefreshTokenRepository]). That keeps the hot path free of
 * a database round trip on every request.
 */
class JwtService(private val config: AppConfig.JwtConfig) {

    val realm: String get() = config.realm
    val accessTokenTtl: Duration get() = config.accessTokenTtl

    private val algorithm: Algorithm = Algorithm.HMAC256(config.secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun createAccessToken(userId: UUID, email: String): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId.toString())
            .withClaim(CLAIM_EMAIL, email)
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + config.accessTokenTtl.inWholeMilliseconds))
            .sign(algorithm)
    }

    companion object {
        const val CLAIM_EMAIL = "email"
        const val AUTH_JWT = "auth-jwt"
    }
}
