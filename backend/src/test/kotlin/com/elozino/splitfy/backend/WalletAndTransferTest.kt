package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.ApiErrorResponse
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AddPaymentMethodRequest
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.CreateTransferRequest
import com.elozino.splitfy.api.dto.NotificationsResponse
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.TopUpRequest
import com.elozino.splitfy.api.dto.WalletOperationResponse
import com.elozino.splitfy.api.dto.WithdrawRequest
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.PaymentMethod
import com.elozino.splitfy.domain.model.PaymentMethodType
import com.elozino.splitfy.domain.model.Transfer
import com.elozino.splitfy.domain.model.TransferKind
import com.elozino.splitfy.domain.model.TransferStatus
import com.elozino.splitfy.domain.model.Wallet
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

class WalletAndTransferTest {

    private val ngn = CurrencyCode.NGN

    private val database = TestDatabase("splitfy_wallet")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `topping up credits the wallet and writes a ledger entry`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val method = client.addCard(ada.token)

        val response = client.post(ApiRoutes.Wallet.TOP_UP) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(TopUpRequest(Money(500_000, ngn), method.id))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(500_000L, response.body<WalletOperationResponse>().wallet.balance.amount)

        val wallet = client.get(ApiRoutes.Wallet.BASE) { bearerAuth(ada.token) }.body<Wallet>()
        assertEquals(500_000L, wallet.balance.amount)
    }

    @Test
    fun `withdrawing more than the balance is rejected`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val method = client.addCard(ada.token)
        client.topUp(ada.token, method.id, 100_000)

        val response = client.post(ApiRoutes.Wallet.WITHDRAW) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(WithdrawRequest(Money(200_000, ngn), method.id))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ApiErrorCode.INSUFFICIENT_FUNDS, response.body<ApiErrorResponse>().code)

        // The failed attempt must leave the balance untouched.
        val wallet = client.get(ApiRoutes.Wallet.BASE) { bearerAuth(ada.token) }.body<Wallet>()
        assertEquals(100_000L, wallet.balance.amount)
    }

    @Test
    fun `a retried top-up with the same key credits only once`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val method = client.addCard(ada.token)

        val request = TopUpRequest(Money(50_000, ngn), method.id, clientRequestId = "retry-1")
        repeat(2) {
            client.post(ApiRoutes.Wallet.TOP_UP) {
                bearerAuth(ada.token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

        val wallet = client.get(ApiRoutes.Wallet.BASE) { bearerAuth(ada.token) }.body<Wallet>()
        assertEquals(50_000L, wallet.balance.amount)
    }

    @Test
    fun `paying someone moves money between both wallets`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val bode = client.register("bode@example.com")
        val method = client.addCard(ada.token)
        client.topUp(ada.token, method.id, 300_000)

        val transfer = client.post(ApiRoutes.Transfers.BASE) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateTransferRequest(
                    kind = TransferKind.PAYMENT,
                    counterpartyId = bode.user.id,
                    amount = Money(120_000, ngn),
                    note = "Dinner",
                )
            )
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<Transfer>()

        assertEquals(TransferStatus.COMPLETED, transfer.status)
        assertEquals(180_000L, client.wallet(ada.token).balance.amount)
        assertEquals(120_000L, client.wallet(bode.token).balance.amount)
    }

    @Test
    fun `a payment beyond the balance leaves both wallets unchanged`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val bode = client.register("bode@example.com")

        val response = client.post(ApiRoutes.Transfers.BASE) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateTransferRequest(TransferKind.PAYMENT, bode.user.id, Money(10_000, ngn))
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ApiErrorCode.INSUFFICIENT_FUNDS, response.body<ApiErrorResponse>().code)
        assertEquals(0L, client.wallet(bode.token).balance.amount)
    }

    @Test
    fun `a request only moves money once the payer approves`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val bode = client.register("bode@example.com")
        val method = client.addCard(bode.token)
        client.topUp(bode.token, method.id, 200_000)

        // Ada asks Bode for money.
        val request = client.post(ApiRoutes.Transfers.BASE) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(CreateTransferRequest(TransferKind.REQUEST, bode.user.id, Money(75_000, ngn)))
        }.body<Transfer>()

        assertEquals(TransferStatus.PENDING, request.status)
        assertEquals(0L, client.wallet(ada.token).balance.amount)

        // The requester cannot approve their own request.
        val selfApprove = client.post(ApiRoutes.Transfers.approve(request.id)) { bearerAuth(ada.token) }
        assertEquals(HttpStatusCode.Forbidden, selfApprove.status)

        val approved = client.post(ApiRoutes.Transfers.approve(request.id)) { bearerAuth(bode.token) }
            .body<Transfer>()
        assertEquals(TransferStatus.COMPLETED, approved.status)
        assertEquals(75_000L, client.wallet(ada.token).balance.amount)
        assertEquals(125_000L, client.wallet(bode.token).balance.amount)
    }

    @Test
    fun `a full card number is refused in place of a provider token`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")

        val response = client.post(ApiRoutes.PaymentMethods.BASE) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(
                AddPaymentMethodRequest(
                    type = PaymentMethodType.CARD,
                    label = "My card",
                    providerToken = "4111111111111111",
                    last4 = "1111",
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.body<ApiErrorResponse>().fieldErrors.any { it.field == "providerToken" })
    }

    @Test
    fun `signing up seeds notifications and they can be marked read`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")

        val initial = client.get(ApiRoutes.Notifications.BASE) { bearerAuth(ada.token) }
            .body<NotificationsResponse>()
        assertTrue(initial.items.isNotEmpty())
        assertEquals(initial.items.size, initial.unreadCount)

        val afterRead = client.post(ApiRoutes.Notifications.READ_ALL) { bearerAuth(ada.token) }
            .body<NotificationsResponse>()
        assertEquals(0, afterRead.unreadCount)
    }

    @Test
    fun `with the wallet disabled its endpoints do not exist`() = testApplication {
        useTestConfig(database, walletEnabled = false)
        val client = jsonClient()
        val ada = client.register("ada@example.com")

        val calls = listOf(
            client.get(ApiRoutes.Wallet.BASE) { bearerAuth(ada.token) },
            client.post(ApiRoutes.Wallet.TOP_UP) {
                bearerAuth(ada.token)
                contentType(ContentType.Application.Json)
                setBody(TopUpRequest(Money(500_000, ngn), "any"))
            },
            client.get(ApiRoutes.PaymentMethods.BASE) { bearerAuth(ada.token) },
            client.get(ApiRoutes.Transfers.BASE) { bearerAuth(ada.token) },
        )
        calls.forEach { assertEquals(HttpStatusCode.NotFound, it.status, it.call.request.url.encodedPath) }

        // Everything else keeps working.
        assertEquals(HttpStatusCode.OK, client.get(ApiRoutes.Notifications.BASE) { bearerAuth(ada.token) }.status)
    }

    // --- helpers -------------------------------------------------------------

    private data class Account(val user: com.elozino.splitfy.domain.model.User, val token: String)

    private suspend fun HttpClient.register(email: String): Account {
        val response: HttpResponse = post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "Password123", email.substringBefore('@')))
        }
        val auth = response.body<AuthResponse>()
        return Account(auth.user, auth.tokens.accessToken)
    }

    private suspend fun HttpClient.addCard(token: String): PaymentMethod =
        post(ApiRoutes.PaymentMethods.BASE) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                AddPaymentMethodRequest(
                    type = PaymentMethodType.CARD,
                    label = "Mastercard",
                    providerToken = "tok_test_${token.takeLast(6)}",
                    brand = "mastercard",
                    last4 = "4679",
                )
            )
        }.body()

    private suspend fun HttpClient.topUp(token: String, methodId: String, minorUnits: Long) {
        post(ApiRoutes.Wallet.TOP_UP) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(TopUpRequest(Money(minorUnits, ngn), methodId))
        }
    }

    private suspend fun HttpClient.wallet(token: String): Wallet =
        get(ApiRoutes.Wallet.BASE) { bearerAuth(token) }.body()
}
