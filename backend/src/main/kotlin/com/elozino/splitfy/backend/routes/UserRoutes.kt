package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.EmailChangeConfirmRequest
import com.elozino.splitfy.api.dto.EmailChangeRequest
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.api.dto.UploadSlotRequest
import com.elozino.splitfy.backend.plugins.RateLimitScope
import com.elozino.splitfy.backend.plugins.scopeName
import com.elozino.splitfy.backend.services.AccountDeletionService
import com.elozino.splitfy.backend.services.DataExportService
import com.elozino.splitfy.backend.services.EmailChangeService
import com.elozino.splitfy.backend.services.SocialAuthService
import com.elozino.splitfy.backend.services.UploadService
import com.elozino.splitfy.backend.services.UserService
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.backend.utils.paginationLimit
import com.elozino.splitfy.backend.utils.requireUser
import com.elozino.splitfy.backend.utils.uuidParam
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.Route

/** Collaborators of the `/users/me/...` account features, grouped to keep the signature readable. */
class AccountServices(
    val accountDeletion: AccountDeletionService,
    val uploads: UploadService,
    val emailChange: EmailChangeService,
    val social: SocialAuthService,
    val export: DataExportService,
)

fun Route.userRoutes(service: UserService, account: AccountServices) {

    get(ApiRoutes.Users.ME) {
        call.respond(HttpStatusCode.OK, service.me(call.requireUser().userId))
    }

    patch(ApiRoutes.Users.ME) {
        val updated = service.updateProfile(call.requireUser().userId, call.receive<UpdateProfileRequest>())
        call.respond(HttpStatusCode.OK, updated)
    }

    // Anonymises the account; refused with 409 while any group balance is unsettled.
    delete(ApiRoutes.Users.ME) {
        account.accountDeletion.delete(call.requireUser().userId)
        call.respond(HttpStatusCode.NoContent)
    }

    post(ApiRoutes.Users.ME_UPLOADS) {
        val slot = account.uploads.requestAvatarSlot(call.receive<UploadSlotRequest>(), call.requireUser().userId)
        call.respond(HttpStatusCode.Created, slot)
    }

    get(ApiRoutes.Users.IDENTITIES) {
        call.respond(HttpStatusCode.OK, account.social.linkedAccounts(call.requireUser().userId))
    }

    // Sending codes, checking codes and building exports get the tighter bucket on
    // top of the general API one.
    rateLimit(RateLimitScope.AUTH_GENERAL.scopeName) {
        post(ApiRoutes.Users.EMAIL_CHANGE) {
            val pending = account.emailChange.request(call.requireUser().userId, call.receive<EmailChangeRequest>())
            call.respond(HttpStatusCode.Accepted, pending)
        }

        post(ApiRoutes.Users.EMAIL_CHANGE_CONFIRM) {
            val user = account.emailChange.confirm(call.requireUser().userId, call.receive<EmailChangeConfirmRequest>())
            call.respond(HttpStatusCode.OK, user)
        }

        post(ApiRoutes.Users.identity("{provider}")) {
            val linked = account.social.link(call.requireUser().userId, call.provider(), call.receive<OAuthSignInRequest>())
            call.respond(HttpStatusCode.OK, linked)
        }

        delete(ApiRoutes.Users.identity("{provider}")) {
            call.respond(HttpStatusCode.OK, account.social.unlink(call.requireUser().userId, call.provider()))
        }

        get(ApiRoutes.Users.EXPORT) {
            val export = account.export.export(call.requireUser().userId)
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, "splitfy-data-${export.exportedAt.toString().take(10)}.json")
                    .toString(),
            )
            // Personal data: no shared cache may keep a copy.
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(HttpStatusCode.OK, export)
        }
    }

    get(ApiRoutes.Users.SEARCH) {
        call.requireUser()
        val query = call.request.queryParameters["q"].orEmpty()
        call.respond(HttpStatusCode.OK, service.search(query, call.paginationLimit()))
    }

    get(ApiRoutes.Users.byId("{userId}")) {
        call.requireUser()
        call.respond(HttpStatusCode.OK, service.publicProfile(call.uuidParam("userId")))
    }
}

private fun ApplicationCall.provider(): OAuthProvider =
    OAuthProvider.entries.firstOrNull { it.path == parameters["provider"] } ?: throw AppException.NotFound("Sign-in provider")
