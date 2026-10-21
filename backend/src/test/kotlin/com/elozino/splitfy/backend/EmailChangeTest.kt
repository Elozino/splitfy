package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.ApiErrorResponse
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.EmailChangeConfirmRequest
import com.elozino.splitfy.api.dto.EmailChangePending
import com.elozino.splitfy.api.dto.EmailChangeRequest
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.api.dto.RefreshTokenRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.backend.services.OtpNotifier
import com.elozino.splitfy.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.dsl.module

class EmailChangeTest {

    private val database = TestDatabase("splitfy_email_change")

    private class CapturingNotifier : OtpNotifier {
        val changeCodes = ConcurrentHashMap<String, String>()
        val notices = CopyOnWriteArrayList<Pair<String, String>>()
        override suspend fun sendPasswordResetCode(email: String, code: String) = Unit
        override suspend fun sendEmailVerificationCode(email: String, code: String) = Unit
        override suspend fun sendEmailChangeCode(email: String, code: String) {
            changeCodes[email] = code
        }
        override suspend fun sendEmailChangedNotice(oldEmail: String, newEmail: String) {
            notices += oldEmail to newEmail
        }
    }

    private val notifier = CapturingNotifier()
    private val overrides = listOf(module { single<OtpNotifier> { notifier } })

    @BeforeTest
    fun setUp() {
        database.reset()
        notifier.changeCodes.clear()
        notifier.notices.clear()
    }

    @Test
    fun `the code goes to the new address and confirming it moves and verifies the account`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        database.markVerified("ada@example.com")

        val pending = client.requestChange(ada.tokens.accessToken, "Ada.New@Example.com")
        assertEquals(HttpStatusCode.Accepted, pending.status)
        assertEquals("ada.new@example.com", pending.body<EmailChangePending>().newEmail)
        assertFalse(notifier.changeCodes.containsKey("ada@example.com"), "Nothing goes to the old address yet")

        val confirmed = client.confirm(ada.tokens.accessToken, notifier.changeCodes.getValue("ada.new@example.com"))

        assertEquals(HttpStatusCode.OK, confirmed.status)
        val user = confirmed.body<User>()
        assertEquals("ada.new@example.com", user.email)
        assertTrue(user.emailVerified)
        assertEquals(listOf("ada@example.com" to "ada.new@example.com"), notifier.notices.toList())
        assertEquals(HttpStatusCode.OK, client.login("ada.new@example.com").status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("ada@example.com").status)
    }

    @Test
    fun `an address a verified account uses is refused`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        client.register("bob@example.com")
        database.markVerified("bob@example.com")

        val response = client.requestChange(ada.tokens.accessToken, "bob@example.com")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ApiErrorCode.EMAIL_ALREADY_REGISTERED, response.body<ApiErrorResponse>().code)
        assertTrue(notifier.changeCodes.isEmpty())
    }

    @Test
    fun `proving an address takes it from an account that never verified it`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val squatter = client.register("ada.work@example.com")

        assertEquals(HttpStatusCode.Accepted, client.requestChange(ada.tokens.accessToken, "ada.work@example.com").status)
        val confirmed = client.confirm(ada.tokens.accessToken, notifier.changeCodes.getValue("ada.work@example.com"))

        assertEquals(HttpStatusCode.OK, confirmed.status)
        assertEquals("ada.work@example.com", confirmed.body<User>().email)
        // Logging in with the address now reaches Ada's account, and the squatter's
        // password and sessions are gone.
        val login = client.login("ada.work@example.com").body<AuthResponse>()
        assertEquals(ada.user.id, login.user.id)
        val refresh = client.post(ApiRoutes.Auth.REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(squatter.tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, refresh.status)
    }

    @Test
    fun `a squatter who verifies before the code is used keeps the address`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        client.register("ada.work@example.com")

        client.requestChange(ada.tokens.accessToken, "ada.work@example.com")
        database.markVerified("ada.work@example.com")
        val response = client.confirm(ada.tokens.accessToken, notifier.changeCodes.getValue("ada.work@example.com"))

        assertEquals(HttpStatusCode.Conflict, response.status)
        val me = client.get(ApiRoutes.Users.ME) { bearerAuth(ada.tokens.accessToken) }.body<User>()
        assertEquals("ada@example.com", me.email)
    }

    @Test
    fun `wrong codes burn the change, and requests are rate limited`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val token = ada.tokens.accessToken

        assertEquals(HttpStatusCode.BadRequest, client.requestChange(token, "ada@example.com").status)
        assertEquals(HttpStatusCode.BadRequest, client.requestChange(token, "not-an-email").status)

        client.requestChange(token, "new@example.com")
        val tooSoon = client.requestChange(token, "other@example.com")
        assertEquals(HttpStatusCode.TooManyRequests, tooSoon.status)
        assertTrue(tooSoon.body<ApiErrorResponse>().message.startsWith("Wait a minute"))

        val code = notifier.changeCodes.getValue("new@example.com")
        val wrong = if (code == "000000") "111111" else "000000"
        repeat(5) { assertEquals(HttpStatusCode.BadRequest, client.confirm(token, wrong).status) }
        assertEquals(HttpStatusCode.BadRequest, client.confirm(token, code).status)

        val me = client.get(ApiRoutes.Users.ME) { bearerAuth(token) }.body<User>()
        assertEquals("ada@example.com", me.email)
        assertTrue(notifier.notices.isEmpty())
    }

    @Test
    fun `changing email needs a session`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val response = jsonClient().post(ApiRoutes.Users.EMAIL_CHANGE) {
            contentType(ContentType.Application.Json)
            setBody(EmailChangeRequest("new@example.com"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private suspend fun HttpClient.register(email: String): AuthResponse = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, PASSWORD, email.substringBefore('@')))
    }.body()

    private suspend fun HttpClient.login(email: String): HttpResponse = post(ApiRoutes.Auth.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, PASSWORD))
    }

    private suspend fun HttpClient.requestChange(token: String, email: String): HttpResponse =
        post(ApiRoutes.Users.EMAIL_CHANGE) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(EmailChangeRequest(email))
        }

    private suspend fun HttpClient.confirm(token: String, code: String): HttpResponse =
        post(ApiRoutes.Users.EMAIL_CHANGE_CONFIRM) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(EmailChangeConfirmRequest(code))
        }

    private companion object {
        const val PASSWORD = "Password123"
    }
}
