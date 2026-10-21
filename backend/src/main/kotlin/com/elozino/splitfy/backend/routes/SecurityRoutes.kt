package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.ChangePasswordRequest
import com.elozino.splitfy.api.dto.MessageResponse
import com.elozino.splitfy.api.dto.RevokedSessionsResponse
import com.elozino.splitfy.api.dto.TwoFactorDisableRequest
import com.elozino.splitfy.api.dto.TwoFactorEnableRequest
import com.elozino.splitfy.backend.plugins.RateLimitScope
import com.elozino.splitfy.backend.plugins.scopeName
import com.elozino.splitfy.backend.services.AuthService
import com.elozino.splitfy.backend.services.TwoFactorService
import com.elozino.splitfy.backend.utils.requireUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** The signed-in user's password, devices and two-step verification. Mounted inside `authenticate`. */
fun Route.securityRoutes(auth: AuthService, twoFactor: TwoFactorService) {

    get(ApiRoutes.Security.BASE) {
        call.respond(HttpStatusCode.OK, auth.securityOverview(call.requireUser().userId))
    }

    get(ApiRoutes.Security.SESSIONS) {
        call.respond(HttpStatusCode.OK, auth.sessions(call.requireUser()))
    }

    delete(ApiRoutes.Security.session("{id}")) {
        auth.revokeSession(call.requireUser(), call.parameters["id"].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }

    post(ApiRoutes.Security.REVOKE_OTHER_SESSIONS) {
        call.respond(HttpStatusCode.OK, RevokedSessionsResponse(auth.revokeOtherSessions(call.requireUser())))
    }

    // Everything that checks a password or a code: per-user, on the tight bucket,
    // on top of the account-wide lockout in TwoFactorService.
    rateLimit(RateLimitScope.AUTH_GENERAL.scopeName) {
        post(ApiRoutes.Security.PASSWORD) {
            auth.changePassword(call.requireUser(), call.receive<ChangePasswordRequest>())
            call.respond(HttpStatusCode.OK, MessageResponse("Password updated. Other devices have been signed out."))
        }

        post(ApiRoutes.Security.TWO_FACTOR_SETUP) {
            val caller = call.requireUser()
            call.respond(HttpStatusCode.OK, twoFactor.setup(caller.userId, caller.email))
        }

        post(ApiRoutes.Security.TWO_FACTOR_ENABLE) {
            val request = call.receive<TwoFactorEnableRequest>()
            call.respond(HttpStatusCode.OK, twoFactor.enable(call.requireUser().userId, request.code))
        }

        post(ApiRoutes.Security.TWO_FACTOR_DISABLE) {
            twoFactor.disable(call.requireUser().userId, call.receive<TwoFactorDisableRequest>())
            call.respond(HttpStatusCode.OK, MessageResponse("Two-step verification is off"))
        }
    }
}
