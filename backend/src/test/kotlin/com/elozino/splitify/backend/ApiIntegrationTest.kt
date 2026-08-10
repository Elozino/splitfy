package com.elozino.splitify.backend

import com.elozino.splitify.api.ApiErrorCode
import com.elozino.splitify.api.ApiErrorResponse
import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.api.dto.AuthResponse
import com.elozino.splitify.api.dto.CreateExpenseRequest
import com.elozino.splitify.api.dto.CreateGroupRequest
import com.elozino.splitify.api.dto.GroupBalancesResponse
import com.elozino.splitify.api.dto.LoginRequest
import com.elozino.splitify.api.dto.RegisterRequest
import com.elozino.splitify.api.dto.SplitPayload
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.Expense
import com.elozino.splitify.domain.model.Group
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.SplitMethod
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class ApiIntegrationTest {

    private val database = TestDatabase("splitfy_api")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `register issues a token pair and rejects a duplicate email`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()

        val first = client.register("ada@example.com")
        assertEquals(HttpStatusCode.Created, first.status)
        val auth = first.body<AuthResponse>()
        assertTrue(auth.tokens.accessToken.isNotBlank())
        assertTrue(auth.tokens.refreshToken.isNotBlank())
        assertEquals("ada@example.com", auth.user.email)

        val duplicate = client.register("ada@example.com")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals(ApiErrorCode.EMAIL_ALREADY_REGISTERED, duplicate.body<ApiErrorResponse>().code)
    }

    @Test
    fun `register rejects a weak password with field level errors`() = testApplication {
        useTestConfig(database)
        val response = jsonClient().post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("ada@example.com", "short", "Ada"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ApiErrorResponse>()
        assertEquals(ApiErrorCode.VALIDATION_FAILED, error.code)
        assertTrue(error.fieldErrors.any { it.field == "password" })
    }

    @Test
    fun `login with a wrong password is rejected`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        client.register("ada@example.com")

        val response = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("ada@example.com", "WrongPassword1"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, response.body<ApiErrorResponse>().code)
    }

    @Test
    fun `protected routes reject an anonymous caller`() = testApplication {
        useTestConfig(database)
        assertEquals(HttpStatusCode.Unauthorized, jsonClient().get(ApiRoutes.Users.ME).status)
    }

    @Test
    fun `refreshing rotates the token and the old one stops working`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val tokens = client.register("ada@example.com").body<AuthResponse>().tokens

        val refreshed = client.post(ApiRoutes.Auth.REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(mapOf("refreshToken" to tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)

        val replay = client.post(ApiRoutes.Auth.REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(mapOf("refreshToken" to tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, replay.status)
    }

    @Test
    fun `an equal split is computed server side and balances resolve`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()

        val ada = client.register("ada@example.com").body<AuthResponse>()
        val bode = client.register("bode@example.com").body<AuthResponse>()

        val group = client.post(ApiRoutes.Groups.BASE) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateGroupRequest(
                    name = "Lagos Trip",
                    currency = CurrencyCode.NGN,
                    memberIds = listOf(bode.user.id),
                )
            )
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<Group>()

        assertEquals(2, group.members.size)

        // 10 000.00 NGN paid by Ada, split equally between two people.
        val expense = client.post(ApiRoutes.Expenses.forGroup(group.id.value)) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateExpenseRequest(
                    description = "Hotel",
                    total = Money(1_000_000, CurrencyCode.NGN),
                    paidBy = ada.user.id,
                    split = SplitPayload(
                        method = SplitMethod.EQUAL,
                        participantIds = listOf(ada.user.id, bode.user.id),
                    ),
                )
            )
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<Expense>()

        assertEquals(1_000_000, expense.shares.sumOf { it.amount.amount })
        assertEquals(listOf(500_000L, 500_000L), expense.shares.map { it.amount.amount })

        val balances = client.get(ApiRoutes.Groups.balances(group.id.value)) {
            bearerAuth(ada.tokens.accessToken)
        }.body<GroupBalancesResponse>()

        assertEquals(0L, balances.balances.sumOf { it.net.amount })
        assertEquals(500_000L, balances.balances.first { it.userId == ada.user.id }.net.amount)
        assertEquals(-500_000L, balances.balances.first { it.userId == bode.user.id }.net.amount)

        val transfer = balances.suggestedTransfers.single()
        assertEquals(bode.user.id, transfer.from)
        assertEquals(ada.user.id, transfer.to)
        assertEquals(500_000L, transfer.amount.amount)
    }

    @Test
    fun `an exact split that does not add up is rejected`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com").body<AuthResponse>()

        val group = client.post(ApiRoutes.Groups.BASE) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = "Solo", currency = CurrencyCode.NGN))
        }.body<Group>()

        val response = client.post(ApiRoutes.Expenses.forGroup(group.id.value)) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateExpenseRequest(
                    description = "Dinner",
                    total = Money(10_000, CurrencyCode.NGN),
                    paidBy = ada.user.id,
                    split = SplitPayload(
                        method = SplitMethod.EXACT,
                        exactAmounts = mapOf(ada.user.id.value to 9_000L),
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ApiErrorCode.SPLIT_INVALID, response.body<ApiErrorResponse>().code)
    }

    @Test
    fun `a non-member cannot see that a group exists`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com").body<AuthResponse>()
        val stranger = client.register("mallory@example.com").body<AuthResponse>()

        val group = client.post(ApiRoutes.Groups.BASE) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = "Private", currency = CurrencyCode.NGN))
        }.body<Group>()

        val response = client.get(ApiRoutes.Groups.byId(group.id.value)) {
            bearerAuth(stranger.tokens.accessToken)
        }
        // 404 rather than 403: confirming the id exists is itself a leak.
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `an expense cannot be created in another group's currency`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com").body<AuthResponse>()

        val group = client.post(ApiRoutes.Groups.BASE) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = "Naira only", currency = CurrencyCode.NGN))
        }.body<Group>()

        val response = client.post(ApiRoutes.Expenses.forGroup(group.id.value)) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateExpenseRequest(
                    description = "Souvenirs",
                    total = Money(5_000, CurrencyCode.USD),
                    paidBy = ada.user.id,
                    split = SplitPayload(SplitMethod.EQUAL, participantIds = listOf(ada.user.id)),
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ApiErrorCode.CURRENCY_MISMATCH, response.body<ApiErrorResponse>().code)
    }

    @Test
    fun `retrying a create with the same idempotency key does not duplicate`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com").body<AuthResponse>()

        val group = client.post(ApiRoutes.Groups.BASE) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = "Flaky network", currency = CurrencyCode.NGN))
        }.body<Group>()

        val request = CreateExpenseRequest(
            description = "Taxi",
            total = Money(3_000, CurrencyCode.NGN),
            paidBy = ada.user.id,
            split = SplitPayload(SplitMethod.EQUAL, participantIds = listOf(ada.user.id)),
            clientRequestId = "retry-me-once",
        )

        val first = client.postExpense(group.id.value, ada.tokens.accessToken, request).body<Expense>()
        val second = client.postExpense(group.id.value, ada.tokens.accessToken, request).body<Expense>()
        assertEquals(first.id, second.id)
    }

    // --- helpers -------------------------------------------------------------

    private suspend fun HttpClient.register(
        email: String,
        password: String = "Password123",
        name: String = email.substringBefore('@'),
    ): HttpResponse = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email = email, password = password, displayName = name))
    }

    private suspend fun HttpClient.postExpense(
        groupId: String,
        token: String,
        request: CreateExpenseRequest,
    ): HttpResponse = post(ApiRoutes.Expenses.forGroup(groupId)) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(request)
    }
}
