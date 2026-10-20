package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.*
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import kotlin.test.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GroupInvitesTest {
    private val database = TestDatabase("splitfy_invites")
    @BeforeTest fun setUp() = database.reset()

    @Test fun `links are shared by members and accepting twice adds only one membership and use`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner")
        val friend = client.register("friend")
        val group = client.createGroup(owner)
        val link = client.invite(group, owner)
        assertEquals(link, client.invite(group, owner))
        val preview = client.get(ApiRoutes.Invites.byToken(link.token)).body<InvitePreview>()
        assertEquals("Flat", preview.groupName)
        assertEquals(1, preview.memberCount)
        assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.Invites.accept(link.token)).status)
        repeat(2) {
            val joined = client.post(ApiRoutes.Invites.accept(link.token)) { bearerAuth(friend) }.body<Group>()
            assertEquals(2, joined.members.size)
        }
        assertEquals(link, client.invite(group, friend))
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { conn ->
            conn.createStatement().use { st -> st.executeQuery("SELECT use_count FROM group_invites").use {
                assertTrue(it.next()); assertEquals(1, it.getInt(1))
            } }
        }
    }

    @Test fun `simultaneous requests reuse one link and count one join`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner")
        val friend = client.register("friend")
        val group = client.createGroup(owner)
        val links = coroutineScope { List(2) { async { client.invite(group, owner) } }.awaitAll() }
        assertEquals(1, links.map { it.token }.distinct().size)
        val joined = coroutineScope { List(2) { async {
            client.post(ApiRoutes.Invites.accept(links.first().token)) { bearerAuth(friend) }
        } }.awaitAll() }
        joined.forEach { assertEquals(HttpStatusCode.OK, it.status) }
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { conn ->
            conn.createStatement().use { st -> st.executeQuery("SELECT use_count FROM group_invites").use {
                assertTrue(it.next()); assertEquals(1, it.getInt(1)); assertFalse(it.next())
            } }
        }
    }

    @Test fun `only admins reset links and outsiders cannot get the group link`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner")
        val friend = client.register("friend")
        val group = client.createGroup(owner)
        val link = client.invite(group, owner)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.Groups.invite(group.id.value)) { bearerAuth(friend) }.status)
        client.post(ApiRoutes.Invites.accept(link.token)) { bearerAuth(friend) }
        assertEquals(HttpStatusCode.Forbidden, client.post(ApiRoutes.Groups.resetInvite(group.id.value)) { bearerAuth(friend) }.status)
        val replacement = client.post(ApiRoutes.Groups.resetInvite(group.id.value)) { bearerAuth(owner) }.body<GroupInvite>()
        assertNotEquals(link.token, replacement.token)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.Invites.byToken(link.token)).status)
        assertEquals(HttpStatusCode.NotFound, client.post(ApiRoutes.Invites.accept(link.token)) { bearerAuth(friend) }.status)
        assertEquals(HttpStatusCode.OK, client.get(ApiRoutes.Invites.byToken(replacement.token)).status)
    }

    @Test fun `expired and archived links cannot be accepted`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner")
        val friend = client.register("friend")
        val group = client.createGroup(owner)
        val expired = client.invite(group, owner)
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { conn ->
            conn.createStatement().use { it.executeUpdate("UPDATE group_invites SET expires_at = NOW() - INTERVAL '1 day'") }
        }
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.Invites.byToken(expired.token)).status)
        assertEquals(HttpStatusCode.NotFound, client.post(ApiRoutes.Invites.accept(expired.token)) { bearerAuth(friend) }.status)
        val fresh = client.invite(group, owner)
        assertNotEquals(expired.token, fresh.token)
        client.post(ApiRoutes.Groups.archive(group.id.value)) { bearerAuth(owner); parameter("archived", true) }
        assertEquals(HttpStatusCode.NotFound, client.post(ApiRoutes.Invites.accept(fresh.token)) { bearerAuth(friend) }.status)
        assertEquals(HttpStatusCode.Conflict, client.get(ApiRoutes.Groups.invite(group.id.value)) { bearerAuth(owner) }.status)
    }

    @Test fun `browser landing page escapes group names and serves verification files`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner")
        val group = client.createGroup(owner, "<b>Flat</b>")
        val link = client.invite(group, owner)
        val page = client.get("/i/${link.token}").body<String>()
        assertTrue(page.contains("&lt;b&gt;Flat&lt;/b&gt;"))
        assertFalse(page.contains("<b>Flat</b>"))
        assertEquals(HttpStatusCode.OK, client.get("/.well-known/assetlinks.json").status)
        assertEquals(HttpStatusCode.OK, client.get("/.well-known/apple-app-site-association").status)
    }

    private suspend fun HttpClient.register(name: String): String = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("$name@example.com", "Password123", name))
    }.body<AuthResponse>().tokens.accessToken

    private suspend fun HttpClient.createGroup(token: String, name: String = "Flat"): Group = post(ApiRoutes.Groups.BASE) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(CreateGroupRequest(name = name, currency = CurrencyCode.NGN))
    }.body()

    private suspend fun HttpClient.invite(group: Group, token: String): GroupInvite = get(ApiRoutes.Groups.invite(group.id.value)) {
        bearerAuth(token)
    }.body()
}
