package com.elozino.splitify.backend.routes

import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.api.dto.UpdateProfileRequest
import com.elozino.splitify.backend.services.UserService
import com.elozino.splitify.backend.utils.paginationLimit
import com.elozino.splitify.backend.utils.requireUser
import com.elozino.splitify.backend.utils.uuidParam
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.Route

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
