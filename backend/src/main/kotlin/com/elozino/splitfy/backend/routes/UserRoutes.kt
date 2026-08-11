package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.backend.services.UserService
import com.elozino.splitfy.backend.utils.paginationLimit
import com.elozino.splitfy.backend.utils.requireUser
import com.elozino.splitfy.backend.utils.uuidParam
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
