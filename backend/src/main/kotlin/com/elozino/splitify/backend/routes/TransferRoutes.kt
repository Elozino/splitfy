package com.elozino.splitify.backend.routes

import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.api.dto.ActivityFilter
import com.elozino.splitify.api.dto.CreateTransferRequest
import com.elozino.splitify.backend.services.ActivityService
import com.elozino.splitify.backend.services.ContactService
import com.elozino.splitify.backend.services.NotificationService
import com.elozino.splitify.backend.services.TransferService
import com.elozino.splitify.backend.utils.paginationLimit
import com.elozino.splitify.backend.utils.requireUser
import com.elozino.splitify.backend.utils.uuidParam
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.Route

fun Route.transferRoutes(service: TransferService) {

    post(ApiRoutes.Transfers.BASE) {
        val transfer = service.create(call.requireUser().userId, call.receive<CreateTransferRequest>())
        call.respond(HttpStatusCode.Created, transfer)
    }

    get(ApiRoutes.Transfers.BASE) {
        call.respond(HttpStatusCode.OK, service.list(call.requireUser().userId, call.paginationLimit()))
    }

    post(ApiRoutes.Transfers.approve("{transferId}")) {
        val transfer = service.approve(call.requireUser().userId, call.uuidParam("transferId"))
        call.respond(HttpStatusCode.OK, transfer)
    }

    post(ApiRoutes.Transfers.decline("{transferId}")) {
        val transfer = service.decline(call.requireUser().userId, call.uuidParam("transferId"))
        call.respond(HttpStatusCode.OK, transfer)
    }
}

fun Route.activityRoutes(service: ActivityService) {

    get(ApiRoutes.Activity.BASE) {
        val filter = call.request.queryParameters["filter"]
            ?.let { raw -> ActivityFilter.entries.firstOrNull { it.name == raw.uppercase() } }
            ?: ActivityFilter.ALL
        call.respond(HttpStatusCode.OK, service.activity(call.requireUser().userId, filter))
    }
}

fun Route.notificationRoutes(service: NotificationService) {

    get(ApiRoutes.Notifications.BASE) {
        call.respond(
            HttpStatusCode.OK,
            service.list(call.requireUser().userId, NotificationService.DEFAULT_LIMIT),
        )
    }

    post(ApiRoutes.Notifications.read("{notificationId}")) {
        val response = service.markRead(call.requireUser().userId, call.uuidParam("notificationId"))
        call.respond(HttpStatusCode.OK, response)
    }

    post(ApiRoutes.Notifications.READ_ALL) {
        call.respond(HttpStatusCode.OK, service.markAllRead(call.requireUser().userId))
    }
}

fun Route.contactRoutes(service: ContactService) {

    get(ApiRoutes.Contacts.BASE) {
        val query = call.request.queryParameters["q"]
        call.respond(HttpStatusCode.OK, service.contacts(call.requireUser().userId, query))
    }

    post(ApiRoutes.Contacts.favorite("{contactId}")) {
        val favorite = call.request.queryParameters["favorite"]?.toBooleanStrictOrNull() ?: true
        val response = service.setFavorite(
            callerId = call.requireUser().userId,
            contactId = call.uuidParam("contactId"),
            favorite = favorite,
        )
        call.respond(HttpStatusCode.OK, response)
    }
}
