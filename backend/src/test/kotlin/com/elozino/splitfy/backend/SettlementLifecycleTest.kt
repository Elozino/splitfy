package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SettlementStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SettlementLifecycleTest {

    private val database = TestDatabase("splitfy_settlements")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `only the recipient confirms, and a confirmed settlement stays confirmed`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada")
        val bode = client.register("bode")
        val group = client.createGroup(ada, bode)
        val settlement = client.recordPayment(group, from = bode, to = ada)

        assertEquals(HttpStatusCode.Forbidden, client.confirm(group, settlement, bode).status)
        assertEquals(HttpStatusCode.OK, client.confirm(group, settlement, ada).status)

        assertEquals(HttpStatusCode.Conflict, client.confirm(group, settlement, ada).status)
        assertEquals(HttpStatusCode.Conflict, client.cancel(group, settlement, bode).status)
        assertEquals(SettlementStatus.CONFIRMED, client.statusOf(group, settlement, ada))
    }

    @Test
    fun `cancelling twice is a no-op and a cancelled settlement cannot be confirmed`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada")
        val bode = client.register("bode")
        val group = client.createGroup(ada, bode)
        val settlement = client.recordPayment(group, from = bode, to = ada)

        repeat(2) {
            val response = client.cancel(group, settlement, bode)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(SettlementStatus.CANCELLED, response.body<Settlement>().status)
        }
        assertEquals(HttpStatusCode.Conflict, client.confirm(group, settlement, ada).status)
    }

    /**
     * The recipient confirms while the payer cancels. Before the status change was a
     * conditional update, both could pass the PENDING check and the later write won,
     * so a payment the recipient had confirmed could end up cancelled.
     */
    @Test
    fun `a simultaneous confirm and cancel has exactly one winner`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada")
        val bode = client.register("bode")
        val group = client.createGroup(ada, bode)

        repeat(10) {
            val settlement = client.recordPayment(group, from = bode, to = ada)
            val (confirm, cancel) = coroutineScope {
                val confirm = async { client.confirm(group, settlement, ada) }
                val cancel = async { client.cancel(group, settlement, bode) }
                confirm.await() to cancel.await()
            }

            val statuses = setOf(confirm.status, cancel.status)
            assertEquals(setOf(HttpStatusCode.OK, HttpStatusCode.Conflict), statuses)
            val winner = if (confirm.status == HttpStatusCode.OK) SettlementStatus.CONFIRMED else SettlementStatus.CANCELLED
            assertEquals(winner, client.statusOf(group, settlement, ada))
        }
    }

    // --- helpers ----------------------------------------------------------------

    private suspend fun HttpClient.register(name: String): AuthResponse = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("$name@example.com", "Password123", name))
    }.body()

    private suspend fun HttpClient.createGroup(owner: AuthResponse, vararg others: AuthResponse): Group =
        post(ApiRoutes.Groups.BASE) {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = "Flat", currency = CurrencyCode.NGN, memberIds = others.map { it.user.id }))
        }.body()

    private suspend fun HttpClient.recordPayment(group: Group, from: AuthResponse, to: AuthResponse): Settlement =
        post(ApiRoutes.Settlements.forGroup(group.id.value)) {
            bearerAuth(from.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateSettlementRequest(from.user.id, to.user.id, Money(2_000, CurrencyCode.NGN)))
        }.body()

    private suspend fun HttpClient.confirm(group: Group, settlement: Settlement, caller: AuthResponse) =
        post(ApiRoutes.Settlements.confirm(group.id.value, settlement.id.value)) {
            bearerAuth(caller.tokens.accessToken)
        }

    private suspend fun HttpClient.cancel(group: Group, settlement: Settlement, caller: AuthResponse) =
        delete(ApiRoutes.Settlements.byId(group.id.value, settlement.id.value)) {
            bearerAuth(caller.tokens.accessToken)
        }

    private suspend fun HttpClient.statusOf(group: Group, settlement: Settlement, caller: AuthResponse): SettlementStatus =
        get(ApiRoutes.Settlements.forGroup(group.id.value)) { bearerAuth(caller.tokens.accessToken) }
            .body<List<Settlement>>()
            .single { it.id == settlement.id }
            .status
}
