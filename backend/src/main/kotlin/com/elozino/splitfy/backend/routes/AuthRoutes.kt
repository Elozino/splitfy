package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.ForgotPasswordRequest
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.api.dto.LogoutRequest
import com.elozino.splitfy.api.dto.MessageResponse
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.api.dto.RefreshTokenRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.ResetPasswordRequest
import com.elozino.splitfy.api.dto.VerifyEmailRequest
import com.elozino.splitfy.api.dto.VerifyOtpRequest
import com.elozino.splitfy.api.dto.VerifyOtpResponse
import com.elozino.splitfy.backend.plugins.RateLimitScope
import com.elozino.splitfy.backend.plugins.scopeName
import com.elozino.splitfy.backend.services.AuthService
import com.elozino.splitfy.backend.services.JwtService
import com.elozino.splitfy.backend.services.SocialAuthService
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.backend.utils.requireUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.Route

fun Route.authRoutes(service: AuthService, social: SocialAuthService) {

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

        post(ApiRoutes.Auth.oauth("{provider}")) {
            val provider = OAuthProvider.entries.firstOrNull { it.path == call.parameters["provider"] }
                ?: throw AppException.NotFound("Sign-in provider")
            call.respond(HttpStatusCode.OK, social.signIn(provider, call.receive<OAuthSignInRequest>()))
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
            post(ApiRoutes.Auth.VERIFY_EMAIL) {
                val user = service.verifyEmail(call.requireUser().userId, call.receive<VerifyEmailRequest>())
                call.respond(HttpStatusCode.OK, user)
            }

            post(ApiRoutes.Auth.RESEND_VERIFICATION) {
                service.resendVerification(call.requireUser().userId)
                call.respond(HttpStatusCode.Accepted, MessageResponse("A new code is on its way"))
            }

            post("${ApiRoutes.Auth.LOGOUT}/all") {
                service.logoutEverywhere(call.requireUser().userId)
                call.respond(HttpStatusCode.OK, MessageResponse("Signed out of all devices"))
            }
        }
    }
}
