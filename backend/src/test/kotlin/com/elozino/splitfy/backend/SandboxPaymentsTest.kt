package com.elozino.splitfy.backend

import com.elozino.splitfy.backend.payments.*
import com.elozino.splitfy.backend.schema.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

class SandboxPaymentsTest {
    private val database = TestDatabase("splitfy_payments")
    private val secret = "sk_test_local_fixture"
    private val repository = PaymentRepository()
    private val provider = object : PaymentProvider {
        override suspend fun initialize(intent: PaymentIntent) = "https://checkout.paystack.com/test"
        override suspend fun withdraw(intent: PaymentIntent) = Unit
        override suspend fun verify(intent: PaymentIntent) = ProviderObservation(intent.reference, intent.amount, intent.currency, "success")
    }
    private fun service(enabled: Boolean = true, source: PaymentProvider = provider) = SandboxPayments(
        repository, source, PaystackWebhookVerifier(secret), SandboxPaymentFlags(enabled, enabled),
    )
    @BeforeTest fun reset() = database.reset()

    @Test fun `flags are off and live keys rejected`() = runTest {
        assertEquals(SandboxPaymentFlags(false, false), SandboxPaymentFlags())
        assertFailsWith<IllegalStateException> { service(false).topUp(UUID.randomUUID(), 100, "key", "a@example.com") }
        HttpClient(MockEngine { error("Must not call network") }).use {
            assertFailsWith<IllegalArgumentException> { PaystackSandboxProvider(it, "sk_live_forbidden") }
        }
    }

    @Test fun `signed webhook credits once and only stores verified reusable authorization`() = testApplication {
        useTestConfig(database, walletEnabled = false); startApplication()
        val user = user()
        val intent = service().topUp(user, 1000, "topup", "a@example.com")
        assertEquals(0, balance(user))
        assertEquals(intent, service().topUp(user, 1000, "topup", "a@example.com"))
        assertFailsWith<IllegalArgumentException> { service().topUp(user, 1001, "topup", "a@example.com") }
        val raw = event(intent, "charge.success", "success")
        assertFailsWith<IllegalArgumentException> { service().webhook(raw, "0".repeat(128)) }
        assertFailsWith<IllegalArgumentException> { service().webhook(raw + ' '.code.toByte(), sign(raw)) }
        assertFailsWith<IllegalArgumentException> { deliver(intent.copy(amount = 999), "charge.success", "success") }
        assertFailsWith<IllegalArgumentException> { deliver(intent.copy(currency = "USD"), "charge.success", "success") }
        assertFailsWith<IllegalArgumentException> { deliver(intent.copy(destination = "other@example.com"), "charge.success", "success") }
        val live = raw.toString(Charsets.UTF_8).replace("\"test\"", "\"live\"").toByteArray()
        assertFailsWith<IllegalArgumentException> { service().webhook(live, sign(live)) }
        assertEquals(0, balance(user))
        coroutineScope { List(4) { async(Dispatchers.IO) { service().webhook(raw, sign(raw)) } }.awaitAll() }
        assertEquals(1000, balance(user))
        assertEquals(PaymentStatus.COMPLETED, repository.find(intent.reference)?.status)
        dbQuery {
            assertEquals(1, PaymentEffects.selectAll().count())
            assertEquals(1, LedgerEntriesTable.selectAll().count())
            assertEquals("AUTH_test", PaymentMethodsTable.selectAll().single()[PaymentMethodsTable.providerToken])
        }
    }

    @Test fun `withdrawals reserve complete and reverse exactly once despite reordered events`() = testApplication {
        useTestConfig(database, walletEnabled = false); startApplication()
        val user = user()
        deliver(service().topUp(user, 1000, "fund", "a@example.com"), "charge.success", "success")
        val withdrawal = service().withdrawal(user, 700, "withdraw", "RCP_test")
        assertEquals(300, balance(user))
        assertEquals(withdrawal, service().withdrawal(user, 700, "withdraw", "RCP_test"))
        assertFailsWith<IllegalArgumentException> { service().withdrawal(user, 301, "too-much", "RCP_test") }
        assertEquals(1, repository.pending().size)
        deliver(withdrawal, "transfer.success", "success")
        assertEquals(PaymentStatus.COMPLETED, repository.find(withdrawal.reference)?.status)
        deliver(withdrawal, "transfer.failed", "failed")
        assertEquals(300, balance(user))
        deliver(withdrawal, "transfer.reversed", "reversed")
        deliver(withdrawal, "transfer.reversed", "reversed")
        deliver(withdrawal, "transfer.success", "success")
        assertEquals(1000, balance(user))
        assertEquals(PaymentStatus.REVERSED, repository.find(withdrawal.reference)?.status)
        val failed = service().withdrawal(user, 500, "failed", "RCP_test")
        deliver(failed, "transfer.failed", "failed")
        deliver(failed, "transfer.reversed", "reversed")
        deliver(failed, "transfer.success", "success")
        assertEquals(1000, balance(user))
        dbQuery { assertEquals(5, LedgerEntriesTable.selectAll().count()) }
    }

    @Test fun `concurrent withdrawals cannot overspend`() = testApplication {
        useTestConfig(database, walletEnabled = false); startApplication()
        val user = user()
        deliver(service().topUp(user, 1000, "fund", "a@example.com"), "charge.success", "success")
        val outcomes = coroutineScope {
            List(2) { n -> async(Dispatchers.IO) { runCatching { service().withdrawal(user, 700, "w$n", "RCP_test") } } }.awaitAll()
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(300, balance(user))
        assertEquals(1, repository.pending().size)
    }

    @Test fun `reconciliation never credits and uncertain dispatch keeps reservation`() = testApplication {
        useTestConfig(database, walletEnabled = false); startApplication()
        val user = user()
        val topup = service().topUp(user, 1000, "fund", "a@example.com")
        assertEquals("success", service().reconcile().single().providerStatus)
        assertEquals(0, balance(user))
        deliver(topup, "charge.success", "success")
        val withdrawal = service().withdrawal(user, 500, "w", "RCP_test")
        val failing = object : PaymentProvider by provider {
            override suspend fun withdraw(intent: PaymentIntent) { throw IllegalStateException("timeout") }
            override suspend fun verify(intent: PaymentIntent): ProviderObservation { throw IllegalStateException("timeout") }
        }
        assertFailsWith<IllegalStateException> { service(source = failing).dispatchWithdrawal(withdrawal.reference) }
        assertEquals(500, balance(user))
        assertEquals(PaymentStatus.PENDING, repository.find(withdrawal.reference)?.status)
        assertEquals("VERIFY_FAILED", service(source = failing).reconcile().single().providerStatus)
        val mismatch = object : PaymentProvider by provider {
            override suspend fun verify(intent: PaymentIntent) = ProviderObservation("wrong", intent.amount, "NGN", "success")
        }
        assertEquals("MISMATCH", service(source = mismatch).reconcile().single().providerStatus)
    }

    @Test fun `adapter sends minor units reference and test auth using mocked HTTP`() = runTest {
        val intent = PaymentIntent(UUID.randomUUID(), UUID.randomUUID(), PaymentKind.TOP_UP, 12345, "NGN", "splitfy-test", "a@example.com", PaymentStatus.PENDING)
        val paths = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            assertEquals("Bearer $secret", request.headers[HttpHeaders.Authorization])
            paths += request.url.encodedPath
            val body = (request.body as? io.ktor.http.content.TextContent)?.text
            if (body != null) {
                val json = Json.parseToJsonElement(body).jsonObject
                assertEquals(12345, json.getValue("amount").jsonPrimitive.long)
                assertEquals("splitfy-test", json.getValue("reference").jsonPrimitive.content)
            }
            respond("""{"status":true,"data":{"reference":"splitfy-test","authorization_url":"https://checkout.paystack.com/test","amount":12345,"currency":"NGN","status":"success"}}""", HttpStatusCode.OK)
        }).use { client ->
            val adapter = PaystackSandboxProvider(client, secret)
            assertEquals("https://checkout.paystack.com/test", adapter.initialize(intent))
            adapter.withdraw(intent.copy(kind = PaymentKind.WITHDRAWAL, destination = "RCP_test"))
            assertEquals(12345, adapter.verify(intent).amount)
            adapter.verify(intent.copy(kind = PaymentKind.WITHDRAWAL))
        }
        assertEquals(listOf("/transaction/initialize", "/transfer", "/transaction/verify/splitfy-test", "/transfer/verify/splitfy-test"), paths)
    }

    private suspend fun user(): UUID = dbQuery {
        UUID.randomUUID().also { id -> UsersTable.insert {
            it[UsersTable.id] = id; it[email] = "a@example.com"; it[passwordHash] = "unused"
            it[displayName] = "Ada"; it[defaultCurrency] = "NGN"; it[createdAt] = Instant.now(); it[updatedAt] = Instant.now()
        } }
    }
    private suspend fun balance(user: UUID): Long = dbQuery { WalletsTable.selectAll().where { WalletsTable.userId eq user }.single()[WalletsTable.balance] }
    private fun event(intent: PaymentIntent, type: String, status: String): ByteArray = buildJsonObject {
        put("event", type)
        putJsonObject("data") {
            put("reference", intent.reference); put("amount", intent.amount); put("currency", intent.currency)
            put("status", status); put("domain", "test")
            putJsonObject("customer") { put("email", intent.destination) }
            putJsonObject("authorization") { put("reusable", true); put("authorization_code", "AUTH_test"); put("last4", "1234"); put("brand", "visa") }
        }
    }.toString().toByteArray()
    private fun sign(raw: ByteArray): String = Mac.getInstance("HmacSHA512").apply {
        init(SecretKeySpec(secret.toByteArray(), "HmacSHA512"))
    }.doFinal(raw).joinToString("") { "%02x".format(it) }
    private suspend fun deliver(intent: PaymentIntent, type: String, status: String) {
        val raw = event(intent, type, status)
        assertTrue(service().webhook(raw, sign(raw)))
    }
}
