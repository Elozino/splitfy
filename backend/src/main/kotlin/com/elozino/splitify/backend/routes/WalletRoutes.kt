package com.elozino.splitify.backend.routes

import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.api.dto.AddPaymentMethodRequest
import com.elozino.splitify.api.dto.TopUpRequest
import com.elozino.splitify.api.dto.WithdrawRequest
import com.elozino.splitify.backend.services.WalletService
import com.elozino.splitify.backend.utils.buildPage
import com.elozino.splitify.backend.utils.Cursor
import com.elozino.splitify.backend.utils.paginationCursor
import com.elozino.splitify.backend.utils.paginationLimit
import com.elozino.splitify.backend.utils.requireUser
import com.elozino.splitify.backend.utils.uuidParam
import com.elozino.splitify.domain.model.LedgerEntryType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.Route
import java.util.UUID

fun Route.walletRoutes(service: WalletService) {

    get(ApiRoutes.Wallet.BASE) {
        call.respond(HttpStatusCode.OK, service.wallet(call.requireUser().userId))
    }

    get(ApiRoutes.Wallet.LEDGER) {
        val limit = call.paginationLimit()
        // `?type=TOP_UP&type=WITHDRAWAL` filters the statement; unknown values are
        // ignored rather than failing the whole request.
        val types = call.request.queryParameters.getAll("type")
            .orEmpty()
            .mapNotNull { raw -> LedgerEntryType.entries.firstOrNull { it.name == raw.uppercase() } }

        val entries = service.ledger(call.requireUser().userId, call.paginationCursor(), limit, types)
        call.respond(
            HttpStatusCode.OK,
            buildPage(entries, limit) { Cursor(it.occurredAt, UUID.fromString(it.id)) },
        )
    }

    post(ApiRoutes.Wallet.TOP_UP) {
        val response = service.topUp(call.requireUser().userId, call.receive<TopUpRequest>())
        call.respond(HttpStatusCode.Created, response)
    }

    post(ApiRoutes.Wallet.WITHDRAW) {
        val response = service.withdraw(call.requireUser().userId, call.receive<WithdrawRequest>())
        call.respond(HttpStatusCode.Created, response)
    }

    get(ApiRoutes.PaymentMethods.BASE) {
        call.respond(HttpStatusCode.OK, service.paymentMethods(call.requireUser().userId))
    }

    post(ApiRoutes.PaymentMethods.BASE) {
        val method = service.addPaymentMethod(
            call.requireUser().userId,
            call.receive<AddPaymentMethodRequest>(),
        )
        call.respond(HttpStatusCode.Created, method)
    }

    post(ApiRoutes.PaymentMethods.makeDefault("{methodId}")) {
        val methods = service.makeDefaultPaymentMethod(
            call.requireUser().userId,
            call.uuidParam("methodId"),
        )
        call.respond(HttpStatusCode.OK, methods)
    }

    delete(ApiRoutes.PaymentMethods.byId("{methodId}")) {
        val methods = service.deletePaymentMethod(call.requireUser().userId, call.uuidParam("methodId"))
        call.respond(HttpStatusCode.OK, methods)
    }
}
