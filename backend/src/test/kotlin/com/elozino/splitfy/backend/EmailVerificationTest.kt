package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AddMemberRequest
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.VerifyEmailRequest
import com.elozino.splitfy.backend.services.OtpNotifier
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.dsl.module

class EmailVerificationTest {

    private val database = TestDatabase("splitfy_verify")

    /** The latest verification code sent to each address. */
    private class CapturingNotifier : OtpNotifier {
        val codes = ConcurrentHashMap<String, String>()
        var sends = 0
        override suspend fun sendPasswordResetCode(email: String, code: String) = Unit
        override suspend fun sendEmailVerificationCode(email: String, code: String) {
            codes[email] = code
            sends++
        }
    }

    private val notifier = CapturingNotifier()
    private val overrides = listOf(module { single<OtpNotifier> { notifier } })

    private data class Account(val user: User, val token: String)

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `signing up sends a code and the account starts unverified`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val ada = jsonClient().register("ada@example.com")

        assertFalse(ada.user.emailVerified)
        assertTrue(notifier.codes.containsKey("ada@example.com"))
    }

    @Test
    fun `the emailed code verifies the account`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")

        val response = client.verify(ada.token, notifier.codes.getValue("ada@example.com"))

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<User>().emailVerified)
        val me = client.get(ApiRoutes.Users.ME) { bearerAuth(ada.token) }.body<User>()
        assertTrue(me.emailVerified)
    }

    @Test
    fun `a wrong code is rejected and five of them burn the code`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val code = notifier.codes.getValue("ada@example.com")
        val wrong = if (code == "000000") "111111" else "000000"

        repeat(5) { assertEquals(HttpStatusCode.BadRequest, client.verify(ada.token, wrong).status) }

        assertEquals(HttpStatusCode.BadRequest, client.verify(ada.token, code).status)
    }

    @Test
    fun `resending inside the cooldown sends nothing new`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")

        val response = client.post(ApiRoutes.Auth.RESEND_VERIFICATION) { bearerAuth(ada.token) }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(1, notifier.sends)
    }

    @Test
    fun `an unverified account cannot be added to a group by email`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val owner = client.register("owner@example.com")
        val squatter = client.register("friend@example.com")
        val group = client.createGroup(owner.token)

        val blocked = client.addByEmail(owner.token, group.id.value, "friend@example.com")
        assertEquals(HttpStatusCode.NotFound, blocked.status)

        client.verify(squatter.token, notifier.codes.getValue("friend@example.com"))
        val allowed = client.addByEmail(owner.token, group.id.value, "friend@example.com")
        assertEquals(HttpStatusCode.OK, allowed.status, "add returned ${allowed.status}")
    }

    @Test
    fun `directory search only matches a full verified email`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val searcher = client.register("searcher@example.com")
        val ada = client.register("ada.lovelace@example.com")
        client.verify(ada.token, notifier.codes.getValue("ada.lovelace@example.com"))

        suspend fun search(q: String) = client.get(ApiRoutes.Users.SEARCH) {
            bearerAuth(searcher.token)
            parameter("q", q)
        }.body<List<User>>()

        assertTrue(search("ada").isEmpty(), "a prefix must not reveal addresses")
        assertTrue(search("Ada Lovelace").isEmpty(), "names are not searchable")
        assertEquals("ada.lovelace@example.com", search("ADA.lovelace@example.com").single().email)
        assertTrue(search("searcher@example.com").isEmpty(), "unverified accounts are not listed")
    }

    private suspend fun HttpClient.register(email: String): Account {
        val auth = post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "Password123", email.substringBefore('@')))
        }.body<AuthResponse>()
        return Account(auth.user, auth.tokens.accessToken)
    }

    private suspend fun HttpClient.verify(token: String, code: String) = post(ApiRoutes.Auth.VERIFY_EMAIL) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(VerifyEmailRequest(code))
    }

    private suspend fun HttpClient.createGroup(token: String): Group = post(ApiRoutes.Groups.BASE) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(CreateGroupRequest(name = "Flat", currency = CurrencyCode.NGN))
    }.body()

    private suspend fun HttpClient.addByEmail(token: String, groupId: String, email: String) =
        post(ApiRoutes.Groups.members(groupId)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(email = email))
        }
}
