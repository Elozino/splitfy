package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.GroupInvite
import com.elozino.splitfy.api.dto.InvitePreview
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InviteTest {

    private val database = TestDatabase("splitfy_invites")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `a member shares a link that previews publicly and lets someone join`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner@example.com", "Owner")
        val friend = client.register("friend@example.com", "Friend")
        val group = client.createGroup(owner.tokens.accessToken)

        val invite = client.get(ApiRoutes.Groups.invite(group.id.value)) { bearerAuth(owner.tokens.accessToken) }
            .body<GroupInvite>()
        assertTrue(invite.url.endsWith("/i/${invite.token}"))

        // The preview needs no session.
        val preview = client.get(ApiRoutes.Invites.byToken(invite.token)).body<InvitePreview>()
        assertEquals("Flat", preview.groupName)
        assertEquals("Owner", preview.invitedBy)
        assertEquals(1, preview.memberCount)

        val joined = client.accept(friend.tokens.accessToken, invite.token)
        assertEquals(HttpStatusCode.OK, joined.status)
        assertEquals(2, joined.body<Group>().members.size)

        // Accepting twice is harmless.
        assertEquals(2, client.accept(friend.tokens.accessToken, invite.token).body<Group>().members.size)
    }

    @Test
    fun `the same link is returned until it is reset`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner@example.com", "Owner")
        val group = client.createGroup(owner.tokens.accessToken)

        val first = client.invite(owner.tokens.accessToken, group.id.value)
        val again = client.invite(owner.tokens.accessToken, group.id.value)
        assertEquals(first.token, again.token)

        val reset = client.post(ApiRoutes.Groups.resetInvite(group.id.value)) { bearerAuth(owner.tokens.accessToken) }
            .body<GroupInvite>()
        assertTrue(reset.token != first.token)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.Invites.byToken(first.token)).status)
    }

    @Test
    fun `only members can get the link and only managers can reset it`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner@example.com", "Owner")
        val stranger = client.register("stranger@example.com", "Stranger")
        val group = client.createGroup(owner.tokens.accessToken)
        val invite = client.invite(owner.tokens.accessToken, group.id.value)

        val outsider = client.get(ApiRoutes.Groups.invite(group.id.value)) { bearerAuth(stranger.tokens.accessToken) }
        assertEquals(HttpStatusCode.NotFound, outsider.status)

        client.accept(stranger.tokens.accessToken, invite.token)
        val memberReset = client.post(ApiRoutes.Groups.resetInvite(group.id.value)) {
            bearerAuth(stranger.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.Forbidden, memberReset.status)
    }

    @Test
    fun `an unknown token is not found and accepting needs a session`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()

        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.Invites.byToken("nope")).status)
        assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.Invites.accept("nope")).status)
    }

    @Test
    fun `the web page names the group and escapes it`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner@example.com", "Owner")
        val group = client.createGroup(owner.tokens.accessToken, name = "<b>Flat</b>")
        val invite = client.invite(owner.tokens.accessToken, group.id.value)

        val page = client.get("/i/${invite.token}").bodyAsText()

        assertTrue("&lt;b&gt;Flat&lt;/b&gt;" in page)
        assertTrue("<b>Flat</b>" !in page)
        assertTrue("splitfy://invite/${invite.token}" in page)
    }

    @Test
    fun `app link verification files are served`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()

        val android = client.get("/.well-known/assetlinks.json")
        val apple = client.get("/.well-known/apple-app-site-association")

        assertEquals(HttpStatusCode.OK, android.status)
        assertTrue("com.elozino.splitfy" in android.bodyAsText())
        assertEquals(HttpStatusCode.OK, apple.status)
        assertTrue("/i/*" in apple.bodyAsText())
    }

    private suspend fun HttpClient.register(email: String, name: String) = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, "Password123", name))
    }.body<AuthResponse>()

    private suspend fun HttpClient.createGroup(token: String, name: String = "Flat"): Group = post(ApiRoutes.Groups.BASE) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(CreateGroupRequest(name = name, currency = CurrencyCode.NGN))
    }.body()

    private suspend fun HttpClient.invite(token: String, groupId: String): GroupInvite =
        get(ApiRoutes.Groups.invite(groupId)) { bearerAuth(token) }.body()

    private suspend fun HttpClient.accept(token: String, inviteToken: String) =
        post(ApiRoutes.Invites.accept(inviteToken)) { bearerAuth(token) }
}
