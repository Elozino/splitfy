package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.UploadSlotRequest
import com.elozino.splitfy.backend.services.UploadService
import com.elozino.splitfy.backend.utils.requireUser
import com.elozino.splitfy.backend.utils.uuidParam
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.Route

fun Route.uploadRoutes(service: UploadService) {

    get(ApiRoutes.Uploads.CONFIG) {
        call.respond(HttpStatusCode.OK, service.config())
    }

    post(ApiRoutes.Groups.uploads("{groupId}")) {
        val slot = service.requestSlot(
            groupId = call.uuidParam("groupId"),
            request = call.receive<UploadSlotRequest>(),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.Created, slot)
    }
}
