package com.elozino.splitify.server.feature.auth

import com.elozino.splitify.server.common.requireUser
import com.elozino.splitify.server.plugins.RateLimitScope
import com.elozino.splitify.server.plugins.scopeName
import com.elozino.splitify.shared.api.ApiRoutes
import com.elozino.splitify.shared.api.dto.ForgotPasswordRequest
import com.elozino.splitify.shared.api.dto.LoginRequest
import com.elozino.splitify.shared.api.dto.LogoutRequest
import com.elozino.splitify.shared.api.dto.MessageResponse
import com.elozino.splitify.shared.api.dto.RefreshTokenRequest
import com.elozino.splitify.shared.api.dto.RegisterRequest
import com.elozino.splitify.shared.api.dto.ResetPasswordRequest
import com.elozino.splitify.shared.api.dto.VerifyOtpRequest
import com.elozino.splitify.shared.api.dto.VerifyOtpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(service: AuthService) {

    // Credential endpoints are the prime target for stuffing attacks: keep them
    // on the tightest bucket.
    rateLimit(RateLimitScope.AUTH_STRICT.scopeName) {

        post(ApiRoutes.Auth.REGISTER) {
            val response = service.register(call.receive<RegisterRequest>())
            call.respond(HttpStatusCode.Created, response)
        }

        post(ApiRoutes.Auth.LOGIN) {
            call.respond(HttpStatusCode.OK, service.login(call.receive<LoginRequest>()))
        }

        post(ApiRoutes.Auth.FORGOT_PASSWORD) {
            service.forgotPassword(call.receive<ForgotPasswordRequest>())
            call.respond(
                HttpStatusCode.Accepted,
                MessageResponse("If that account exists, a reset code is on its way"),
            )
        }

        post(ApiRoutes.Auth.VERIFY_OTP) {
            val resetToken = service.verifyOtp(call.receive<VerifyOtpRequest>())
            call.respond(HttpStatusCode.OK, VerifyOtpResponse(resetToken))
        }

        post(ApiRoutes.Auth.RESET_PASSWORD) {
            service.resetPassword(call.receive<ResetPasswordRequest>())
            call.respond(HttpStatusCode.OK, MessageResponse("Password updated"))
        }
    }

    // Refresh is called by every active client roughly once per access-token
    // lifetime, so it gets its own, roomier bucket.
    rateLimit(RateLimitScope.AUTH_REFRESH.scopeName) {
        post(ApiRoutes.Auth.REFRESH) {
            val request = call.receive<RefreshTokenRequest>()
            call.respond(HttpStatusCode.OK, service.refresh(request.refreshToken))
        }
    }

    rateLimit(RateLimitScope.AUTH_GENERAL.scopeName) {
        post(ApiRoutes.Auth.LOGOUT) {
            service.logout(call.receive<LogoutRequest>().refreshToken)
            call.respond(HttpStatusCode.OK, MessageResponse("Signed out"))
        }

        authenticate(JwtService.AUTH_JWT) {
            post("${ApiRoutes.Auth.LOGOUT}/all") {
                service.logoutEverywhere(call.requireUser().userId)
                call.respond(HttpStatusCode.OK, MessageResponse("Signed out of all devices"))
            }
        }
    }
}
