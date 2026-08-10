package com.elozino.splitify.server.plugins

import com.elozino.splitify.server.common.UserPrincipal
import com.elozino.splitify.server.feature.auth.JwtService
import com.elozino.splitify.shared.api.ApiErrorCode
import com.elozino.splitify.shared.api.ApiErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import java.util.UUID
import org.koin.ktor.ext.inject

/**
 * Bearer-token authentication.
 *
 * The JWT is verified for signature, issuer, audience and expiry, then translated
 * into a [UserPrincipal] — routes never touch raw claims.
 */
fun Application.configureSecurity() {
    val jwtService by inject<JwtService>()

    authentication {
        jwt(JwtService.AUTH_JWT) {
            realm = jwtService.realm
            verifier(jwtService.verifier)

            validate { credential ->
                val subject = credential.payload.subject ?: return@validate null
                val email = credential.payload.getClaim(JwtService.CLAIM_EMAIL).asString()
                    ?: return@validate null
                val userId = runCatching { UUID.fromString(subject) }.getOrNull() ?: return@validate null
                UserPrincipal(userId, email)
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorResponse(
                        code = ApiErrorCode.TOKEN_INVALID,
                        message = "A valid access token is required",
                    ),
                )
            }
        }
    }
}
