package com.elozino.splitify.server.feature.user

import com.elozino.splitify.server.common.paginationLimit
import com.elozino.splitify.server.common.requireUser
import com.elozino.splitify.server.common.uuidParam
import com.elozino.splitify.shared.api.ApiRoutes
import com.elozino.splitify.shared.api.dto.UpdateProfileRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch

fun Route.userRoutes(service: UserService) {

    get(ApiRoutes.Users.ME) {
        call.respond(HttpStatusCode.OK, service.me(call.requireUser().userId))
    }

    patch(ApiRoutes.Users.ME) {
        val updated = service.updateProfile(call.requireUser().userId, call.receive<UpdateProfileRequest>())
        call.respond(HttpStatusCode.OK, updated)
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
