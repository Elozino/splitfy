package com.elozino.splitify.backend.routes

import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.api.dto.CreateSettlementRequest
import com.elozino.splitify.backend.services.SettlementService
import com.elozino.splitify.backend.utils.requireUser
import com.elozino.splitify.backend.utils.uuidParam
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.Route

fun Route.settlementRoutes(service: SettlementService) {

    post(ApiRoutes.Settlements.forGroup("{groupId}")) {
        val settlement = service.create(
            groupId = call.uuidParam("groupId"),
            request = call.receive<CreateSettlementRequest>(),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.Created, settlement)
    }

    get(ApiRoutes.Settlements.forGroup("{groupId}")) {
        call.respond(
            HttpStatusCode.OK,
            service.list(call.uuidParam("groupId"), call.requireUser().userId),
        )
    }

    post(ApiRoutes.Settlements.confirm("{groupId}", "{settlementId}")) {
        val settlement = service.confirm(
            groupId = call.uuidParam("groupId"),
            settlementId = call.uuidParam("settlementId"),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.OK, settlement)
    }

    delete(ApiRoutes.Settlements.byId("{groupId}", "{settlementId}")) {
        val settlement = service.cancel(
            groupId = call.uuidParam("groupId"),
            settlementId = call.uuidParam("settlementId"),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.OK, settlement)
    }
}
