package com.elozino.splitify.backend.plugins

import com.elozino.splitify.backend.utils.UserPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_REFILL_PERIOD_SECONDS = 60L

/**
 * Named rate-limit buckets.
 *
 * Limits are per authenticated user where possible and per client IP otherwise, so
 * one abusive device cannot exhaust the budget of everyone behind the same NAT.
 */
enum class RateLimitScope(
    val limit: Int,
    val refillSeconds: Long = DEFAULT_REFILL_PERIOD_SECONDS,
) {
    /** Login, register, password reset: the credential-stuffing surface. */
    AUTH_STRICT(5),

    /** Token refresh: called once per access-token lifetime by every live client. */
    AUTH_REFRESH(20),

    /** Logout and OTP checks. */
    AUTH_GENERAL(10),

    /** Everything behind authentication. */
    API(120),
}

val RateLimitScope.scopeName: RateLimitName get() = RateLimitName(name)

fun Application.configureRateLimiting() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 300, refillPeriod = DEFAULT_REFILL_PERIOD_SECONDS.seconds)
            requestKey { call -> call.rateLimitKey() }
        }

        RateLimitScope.entries.forEach { scope ->
            register(scope.scopeName) {
                rateLimiter(limit = scope.limit, refillPeriod = scope.refillSeconds.seconds)
                requestKey { call -> call.rateLimitKey() }
            }
        }
    }
}

/**
 * Prefer the authenticated user id; fall back to the remote host.
 *
 * `origin.remoteHost` respects `X-Forwarded-For` only because ForwardedHeaders is
 * installed behind a trusted proxy — see [configureHttp].
 */
private fun io.ktor.server.application.ApplicationCall.rateLimitKey(): Any =
    principal<UserPrincipal>()?.userId?.toString() ?: request.origin.remoteHost
