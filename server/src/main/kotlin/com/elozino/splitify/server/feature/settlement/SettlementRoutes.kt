package com.elozino.splitify.server.feature.settlement

import com.elozino.splitify.server.common.requireUser
import com.elozino.splitify.server.common.uuidParam
import com.elozino.splitify.shared.api.ApiRoutes
import com.elozino.splitify.shared.api.dto.CreateSettlementRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

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
