package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.api.dto.DevicePlatform
import com.elozino.splitfy.api.dto.NotificationPreferences
import com.elozino.splitfy.api.dto.NotificationsResponse
import com.elozino.splitfy.api.dto.PushData
import com.elozino.splitfy.api.dto.RegisterDeviceRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.SplitPayload
import com.elozino.splitfy.api.dto.UnregisterDeviceRequest
import com.elozino.splitfy.api.dto.UpdateNotificationPreferencesRequest
import com.elozino.splitfy.backend.services.PaymentReminderJob
import com.elozino.splitfy.backend.services.PushDispatcher
import com.elozino.splitfy.backend.services.PushMessage
import com.elozino.splitfy.backend.services.PushResult
import com.elozino.splitfy.backend.services.PushSender
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SplitMethod
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.Koin
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin

class PushNotificationsTest {

    private val database = TestDatabase("splitfy_push")

    @BeforeTest
    fun setUp() = database.reset()

    /** Records every push; tokens in [dead] are answered the way FCM answers an uninstalled app. */
    private class FakeSender(val dead: Set<String> = emptySet()) : PushSender {
        val sent = CopyOnWriteArrayList<Pair<String, PushMessage>>()

        override suspend fun send(token: String, message: PushMessage): PushResult {
            if (token in dead) return PushResult.INVALID_TOKEN
            sent += token to message
            return PushResult.SENT
        }

        fun ofType(type: String) = sent.filter { it.second.data[PushData.TYPE] == type }
    }

    private fun ApplicationTestBuilder.start(sender: FakeSender): () -> Koin {
        var koin: Koin? = null
        useTestConfig(database, overrides = listOf(module { single<PushSender> { sender } }))
        application { koin = getKoin() }
        return { koin ?: error("application not started") }
    }

    @Test
    fun `registering is idempotent and a token follows whoever registered it last`() = testApplication {
        start(FakeSender())
        val client = jsonClient()
        val ada = client.register("ada")
        val bode = client.register("bode")

        repeat(2) { assertEquals(HttpStatusCode.NoContent, client.registerDevice(ada, "shared-phone").status) }
        assertEquals(listOf("shared-phone" to ada.user.id.value), deviceRows())

        client.registerDevice(bode, "shared-phone")
        assertEquals(listOf("shared-phone" to bode.user.id.value), deviceRows())

        // Ada no longer owns it, so her unregister must not silence Bode.
        client.unregister(ada, "shared-phone")
        assertEquals(1, deviceRows().size)
        repeat(2) { assertEquals(HttpStatusCode.NoContent, client.unregister(bode, "shared-phone").status) }
        assertEquals(emptyList(), deviceRows())

        assertEquals(HttpStatusCode.BadRequest, client.registerDevice(ada, "  ").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.Devices.BASE).status)
    }

    @Test
    fun `expense pushes go to other members and respect their preferences`() = testApplication {
        val sender = FakeSender()
        val koin = start(sender)
        val client = jsonClient()
        val ada = client.register("ada")
        val bode = client.register("bode")
        val chi = client.register("chi")
        listOf(ada to "ada-phone", bode to "bode-phone", chi to "chi-phone").forEach { (user, token) ->
            client.registerDevice(user, token)
        }
        val group = client.createGroup(ada, bode, chi)

        assertEquals(NotificationPreferences(), client.preferences(chi))
        val updated = client.patch(ApiRoutes.Notifications.PREFERENCES) {
            bearerAuth(chi.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(UpdateNotificationPreferencesRequest(newExpenses = false))
        }.body<NotificationPreferences>()
        assertEquals(NotificationPreferences(newExpenses = false), updated)
        assertEquals(updated, client.preferences(chi))

        client.addExpense(group, ada, ada, ada, bode, chi)
        koin().get<PushDispatcher>().drain()

        val pushes = sender.ofType("expense")
        assertEquals(listOf("bode-phone"), pushes.map { it.first })
        assertEquals(group.id.value, pushes.single().second.data[PushData.GROUP_ID])
        assertTrue(pushes.single().second.body.contains("Your share: NGN 30.00"), pushes.single().second.body)

        // Preferences gate the push only: Chi still sees it in the app.
        val inbox = client.get(ApiRoutes.Notifications.BASE) { bearerAuth(chi.tokens.accessToken) }
            .body<NotificationsResponse>()
        assertTrue(inbox.items.any { it.title == "New expense in Flat" })
    }

    @Test
    fun `settlements notify the counterparty when recorded and the payer when confirmed`() = testApplication {
        val sender = FakeSender()
        val koin = start(sender)
        val client = jsonClient()
        val ada = client.register("ada")
        val bode = client.register("bode")
        client.registerDevice(ada, "ada-phone")
        client.registerDevice(bode, "bode-phone")
        val group = client.createGroup(ada, bode)

        val settlement = client.post(ApiRoutes.Settlements.forGroup(group.id.value)) {
            bearerAuth(bode.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateSettlementRequest(bode.user.id, ada.user.id, Money(2_000, CurrencyCode.NGN)))
        }.body<Settlement>()
        koin().get<PushDispatcher>().drain()
        assertEquals(listOf("ada-phone"), sender.ofType("settlement").map { it.first })

        client.post(ApiRoutes.Settlements.confirm(group.id.value, settlement.id.value)) {
            bearerAuth(ada.tokens.accessToken)
        }
        koin().get<PushDispatcher>().drain()
        assertEquals(listOf("ada-phone", "bode-phone"), sender.ofType("settlement").map { it.first })
        assertEquals("ada confirmed your payment", sender.ofType("settlement").last().second.title)
    }

    @Test
    fun `tokens fcm reports as unregistered are deleted`() = testApplication {
        val sender = FakeSender(dead = setOf("uninstalled"))
        val koin = start(sender)
        val client = jsonClient()
        val ada = client.register("ada")
        val bode = client.register("bode")
        client.registerDevice(bode, "uninstalled")
        client.registerDevice(bode, "current-phone")
        val group = client.createGroup(ada, bode)

        client.addExpense(group, ada, ada, bode)
        koin().get<PushDispatcher>().drain()

        assertEquals(listOf("current-phone"), sender.ofType("expense").map { it.first })
        assertEquals(listOf("current-phone" to bode.user.id.value), deviceRows())
    }

    @Test
    fun `payment reminders go out once for old debts and honour the opt-out`() = testApplication {
        val sender = FakeSender()
        val koin = start(sender)
        val client = jsonClient()
        val ada = client.register("ada")
        val bode = client.register("bode")
        val chi = client.register("chi")
        listOf(bode to "bode-phone", chi to "chi-phone").forEach { (user, token) -> client.registerDevice(user, token) }
        val group = client.createGroup(ada, bode, chi)
        client.addExpense(group, ada, ada, ada, bode, chi)
        client.patch(ApiRoutes.Notifications.PREFERENCES) {
            bearerAuth(chi.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(UpdateNotificationPreferencesRequest(paymentReminders = false))
        }
        val job = koin().get<PaymentReminderJob>()
        val today = LocalDate.of(2026, 10, 20)

        // Too recent: the debt is minutes old.
        assertEquals(0, job.runOnce(today.minusDays(1)))

        sql("UPDATE expenses SET created_at = now() - INTERVAL '5 days'")
        assertEquals(1, job.runOnce(today))
        // Same day again, as a second instance would: the claim stops it.
        assertEquals(0, job.runOnce(today))
        // Next morning: within the reminder interval, so no repeat.
        assertEquals(0, job.runOnce(today.plusDays(1)))
        koin().get<PushDispatcher>().drain()

        val reminders = sender.ofType("reminder")
        assertEquals(listOf("bode-phone"), reminders.map { it.first })
        assertEquals("You owe NGN 30.00 in Flat", reminders.single().second.title)
        assertEquals("Settle up with ada when you can.", reminders.single().second.body)
    }

    // --- helpers ----------------------------------------------------------------

    private suspend fun HttpClient.register(name: String): AuthResponse = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("$name@example.com", "Password123", name))
    }.body()

    private suspend fun HttpClient.registerDevice(user: AuthResponse, token: String) = post(ApiRoutes.Devices.BASE) {
        bearerAuth(user.tokens.accessToken)
        contentType(ContentType.Application.Json)
        setBody(RegisterDeviceRequest(token, DevicePlatform.ANDROID))
    }

    private suspend fun HttpClient.unregister(user: AuthResponse, token: String) = post(ApiRoutes.Devices.UNREGISTER) {
        bearerAuth(user.tokens.accessToken)
        contentType(ContentType.Application.Json)
        setBody(UnregisterDeviceRequest(token))
    }

    private suspend fun HttpClient.preferences(user: AuthResponse): NotificationPreferences =
        get(ApiRoutes.Notifications.PREFERENCES) { bearerAuth(user.tokens.accessToken) }.body()

    private suspend fun HttpClient.createGroup(owner: AuthResponse, vararg others: AuthResponse): Group =
        post(ApiRoutes.Groups.BASE) {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = "Flat", currency = CurrencyCode.NGN, memberIds = others.map { it.user.id }))
        }.body()

    /** NGN 90.00 paid by [payer], split equally between [participants]. */
    private suspend fun HttpClient.addExpense(
        group: Group,
        author: AuthResponse,
        payer: AuthResponse,
        vararg participants: AuthResponse,
    ) = post(ApiRoutes.Expenses.forGroup(group.id.value)) {
        bearerAuth(author.tokens.accessToken)
        contentType(ContentType.Application.Json)
        setBody(
            CreateExpenseRequest(
                description = "Groceries",
                total = Money(9_000, CurrencyCode.NGN),
                paidBy = payer.user.id,
                split = SplitPayload(SplitMethod.EQUAL, participantIds = participants.map { it.user.id }),
            )
        )
    }.also { assertEquals(HttpStatusCode.Created, it.status) }

    private fun deviceRows(): List<Pair<String, String>> =
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT token, user_id FROM device_tokens ORDER BY token").use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1) to rows.getString(2)) }
                }
            }
        }

    private fun sql(statement: String) {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { it.executeUpdate(statement) }
        }
    }
}
