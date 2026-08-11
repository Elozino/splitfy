package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.MessageResponse
import com.elozino.splitfy.api.dto.UpdateExpenseRequest
import com.elozino.splitfy.backend.services.ExpenseService
import com.elozino.splitfy.backend.utils.buildPage
import com.elozino.splitfy.backend.utils.Cursor
import com.elozino.splitfy.backend.utils.paginationCursor
import com.elozino.splitfy.backend.utils.paginationLimit
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
import java.util.UUID

fun Route.expenseRoutes(service: ExpenseService) {

    post(ApiRoutes.Expenses.forGroup("{groupId}")) {
        val expense = service.create(
            groupId = call.uuidParam("groupId"),
            request = call.receive<CreateExpenseRequest>(),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.Created, expense)
    }

    get(ApiRoutes.Expenses.forGroup("{groupId}")) {
        val limit = call.paginationLimit()
        val expenses = service.list(
            groupId = call.uuidParam("groupId"),
            callerId = call.requireUser().userId,
            cursor = call.paginationCursor(),
            limit = limit,
        )
        call.respond(
            HttpStatusCode.OK,
            buildPage(expenses, limit) { Cursor(it.createdAt, UUID.fromString(it.id.value)) },
        )
    }

    get(ApiRoutes.Expenses.byId("{groupId}", "{expenseId}")) {
        val expense = service.get(
            groupId = call.uuidParam("groupId"),
            expenseId = call.uuidParam("expenseId"),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.OK, expense)
    }

    patch(ApiRoutes.Expenses.byId("{groupId}", "{expenseId}")) {
        val expense = service.update(
            groupId = call.uuidParam("groupId"),
            expenseId = call.uuidParam("expenseId"),
            request = call.receive<UpdateExpenseRequest>(),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.OK, expense)
    }

    delete(ApiRoutes.Expenses.byId("{groupId}", "{expenseId}")) {
        service.delete(
            groupId = call.uuidParam("groupId"),
            expenseId = call.uuidParam("expenseId"),
            callerId = call.requireUser().userId,
        )
        call.respond(HttpStatusCode.OK, MessageResponse("Expense removed"))
    }
}
