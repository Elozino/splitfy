package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AddMemberRequest
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.UpdateGroupRequest
import com.elozino.splitfy.backend.services.GroupService
import com.elozino.splitfy.backend.utils.requireUser
import com.elozino.splitfy.backend.utils.uuidParam
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.Route

fun Route.groupRoutes(service: GroupService) {

    post(ApiRoutes.Groups.BASE) {
        val group = service.create(call.receive<CreateGroupRequest>(), call.requireUser().userId)
        call.respond(HttpStatusCode.Created, group)
    }

    get(ApiRoutes.Groups.BASE) {
        val includeArchived = call.request.queryParameters["includeArchived"].toBoolean()
        call.respond(HttpStatusCode.OK, service.list(call.requireUser().userId, includeArchived))
    }

    get(ApiRoutes.Groups.byId("{groupId}")) {
        call.respond(HttpStatusCode.OK, service.get(call.uuidParam("groupId"), call.requireUser().userId))
    }

    patch(ApiRoutes.Groups.byId("{groupId}")) {
        val updated = service.update(
            groupId = call.uuidParam("groupId"),
            request = call.receive<UpdateGroupRequest>(),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.OK, updated)
    }

    post(ApiRoutes.Groups.archive("{groupId}")) {
        val archived = call.request.queryParameters["archived"]?.toBooleanStrictOrNull() ?: true
        call.respond(
            HttpStatusCode.OK,
            service.setArchived(call.uuidParam("groupId"), archived, call.requireUser().userId),
        )
    }

    get(ApiRoutes.Groups.balances("{groupId}")) {
        call.respond(
            HttpStatusCode.OK,
            service.balances(call.uuidParam("groupId"), call.requireUser().userId),
        )
    }

    post(ApiRoutes.Groups.members("{groupId}")) {
        val group = service.addMember(
            groupId = call.uuidParam("groupId"),
            request = call.receive<AddMemberRequest>(),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.OK, group)
    }

    delete(ApiRoutes.Groups.member("{groupId}", "{userId}")) {
        val group = service.removeMember(
            groupId = call.uuidParam("groupId"),
            memberId = call.uuidParam("userId"),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.OK, group)
    }
}
