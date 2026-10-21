package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.ForgotPasswordRequest
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.ResetPasswordRequest
import com.elozino.splitfy.api.dto.VerifyOtpRequest
import com.elozino.splitfy.api.dto.VerifyOtpResponse
import com.elozino.splitfy.backend.services.OtpNotifier
import com.elozino.splitfy.backend.services.ResendOtpNotifier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module

class PasswordResetTest {

    private val database = TestDatabase("splitfy_reset")

    /** Captures codes instead of emailing them. */
    private class CapturingNotifier : OtpNotifier {
        val sent = CopyOnWriteArrayList<Pair<String, String>>()
        override suspend fun sendPasswordResetCode(email: String, code: String) {
            sent += email to code
        }

        override suspend fun sendEmailVerificationCode(email: String, code: String) = Unit
        override suspend fun sendEmailChangeCode(email: String, code: String) = Unit
        override suspend fun sendEmailChangedNotice(oldEmail: String, newEmail: String) = Unit
    }

    private val notifier = CapturingNotifier()
    private val overrides = listOf(module { single<OtpNotifier> { notifier } })

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `a user can reset their password with the emailed code`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        client.register("ada@example.com")

        client.forgot("ada@example.com")
        val code = notifier.sent.single().second

        val token = client.post(ApiRoutes.Auth.VERIFY_OTP) {
            contentType(ContentType.Application.Json)
            setBody(VerifyOtpRequest("ada@example.com", code))
        }.body<VerifyOtpResponse>().resetToken

        val reset = client.post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest("ada@example.com", token, "NewPassword456"))
        }
        assertTrue(reset.status.value in 200..299, "reset returned ${reset.status}")

        val login = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("ada@example.com", "NewPassword456"))
        }
        assertEquals(HttpStatusCode.OK, login.status)
    }

    @Test
    fun `a second request inside the cooldown sends no second email`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        client.register("ada@example.com")

        client.forgot("ada@example.com")
        client.forgot("ada@example.com")

        assertEquals(1, notifier.sent.size)
    }

    @Test
    fun `an unknown address looks the same as a known one and sends nothing`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()

        val response = client.forgot("nobody@example.com")

        assertTrue(response.status.value in 200..299)
        assertTrue(notifier.sent.isEmpty())
    }

    @Test
    fun `the code stops working after five wrong guesses`() = testApplication {
        // Behind a proxy (not LOCAL), so X-Forwarded-For is honoured.
        useTestConfig(database, overrides = overrides, environment = "STAGING")
        val client = jsonClient()
        client.register("ada@example.com")
        client.forgot("ada@example.com")
        val code = notifier.sent.single().second
        val wrong = if (code == "000000") "111111" else "000000"

        // Each guess from a different address, as an attacker rotating IPs would:
        // the per-IP rate limit never trips, so only the attempt counter stops them.
        repeat(5) { attempt ->
            val response = client.verify("ada@example.com", wrong, fromIp = "203.0.113.${attempt + 1}")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

        val withRightCode = client.verify("ada@example.com", code, fromIp = "203.0.113.99")
        assertEquals(HttpStatusCode.Unauthorized, withRightCode.status)
    }

    @Test
    fun `Resend receives the code addressed to the user`() = runTest {
        var captured: String? = null
        var auth: String? = null
        val engine = MockEngine { request ->
            captured = request.body.toByteArray().decodeToString()
            auth = request.headers[HttpHeaders.Authorization]
            respond("""{"id":"email_1"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val notifier = ResendOtpNotifier(HttpClient(engine) { install(ContentNegotiation) { json() } }, "re_test", "splitfy <no-reply@splitfy.app>")

        notifier.sendPasswordResetCode("ada@example.com", "123456")

        val body = Json.parseToJsonElement(captured!!).jsonObject
        assertEquals("Bearer re_test", auth)
        assertEquals("ada@example.com", body["to"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("splitfy <no-reply@splitfy.app>", body["from"]!!.jsonPrimitive.content)
        assertTrue("123456" in body["html"]!!.jsonPrimitive.content)
        assertTrue("123456" in body["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a rejected email is an error the caller can log`() = runTest {
        val engine = MockEngine { respond("""{"message":"domain not verified"}""", HttpStatusCode.Forbidden) }
        val notifier = ResendOtpNotifier(HttpClient(engine) { install(ContentNegotiation) { json() } }, "re_test", "a@b.c")

        val error = assertFailsWith<IllegalStateException> { notifier.sendPasswordResetCode("ada@example.com", "123456") }
        assertTrue("domain not verified" in error.message!!)
    }

    private suspend fun HttpClient.register(email: String) = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, "Password123", "Ada"))
    }.body<AuthResponse>()

    private suspend fun HttpClient.forgot(email: String) = post(ApiRoutes.Auth.FORGOT_PASSWORD) {
        contentType(ContentType.Application.Json)
        setBody(ForgotPasswordRequest(email))
    }

    private suspend fun HttpClient.verify(email: String, code: String, fromIp: String? = null) = post(ApiRoutes.Auth.VERIFY_OTP) {
        fromIp?.let { header("X-Forwarded-For", it) }
        contentType(ContentType.Application.Json)
        setBody(VerifyOtpRequest(email, code))
    }
}
