package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AddMemberRequest
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.SplitPayload
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadSlot
import com.elozino.splitfy.api.dto.UploadSlotRequest
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Profile photos and "Download my data". */
class AccountDataTest {

    private val database = TestDatabase("splitfy_account_data")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `the export holds the caller's profile, groups, expenses, settlements and notifications`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada")
        val bob = client.register("bob")
        val carol = client.register("carol")

        val flat = client.createGroup(ada.token, "Flat").withMember(client, ada.token, bob)
        client.post(ApiRoutes.Expenses.forGroup(flat.id.value)) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateExpenseRequest(
                    description = "Groceries",
                    total = Money(5_000, CurrencyCode.NGN),
                    paidBy = UserId(ada.id),
                    split = SplitPayload(SplitMethod.EQUAL, listOf(UserId(ada.id), UserId(bob.id))),
                )
            )
        }
        client.post(ApiRoutes.Settlements.forGroup(flat.id.value)) {
            bearerAuth(bob.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSettlementRequest(UserId(bob.id), UserId(ada.id), Money(2_500, CurrencyCode.NGN)))
        }
        // Carol's own group: nothing of it may show up in Ada's export.
        client.createGroup(carol.token, "Carol's trip")

        val response = client.get(ApiRoutes.Users.EXPORT) { bearerAuth(ada.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("attachment"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val body = response.bodyAsText()
        val export = Json.parseToJsonElement(body).jsonObject

        val profile = export.getValue("profile").jsonObject
        assertEquals("ada@example.com", profile.string("email"))
        assertTrue(profile.getValue("hasPassword").jsonPrimitive.boolean)
        assertEquals(listOf("Flat"), export.array("groups").map { it.string("name") })

        val expense = export.array("expenses").single()
        assertEquals("Groceries", expense.string("description"))
        assertEquals("50.00", expense.string("amount"))
        assertEquals("25.00", expense.string("yourShare"))
        assertTrue(expense.getValue("paidByYou").jsonPrimitive.boolean)

        val settlement = export.array("settlements").single()
        assertEquals("RECEIVED", settlement.string("direction"))
        assertEquals("bob", settlement.string("counterparty"))

        assertTrue(export.array("notifications").isNotEmpty(), "The welcome notification is Ada's data too")

        // Other people appear by name only, and no credential is ever exported.
        assertFalse(body.contains("bob@example.com"))
        assertFalse(body.contains("Carol's trip"))
        assertFalse(body.contains("\$2a\$"), "Password hashes must not be exported")

        // Bob's export is Bob's: he paid the settlement and shares the expense.
        val bobs = Json.parseToJsonElement(
            client.get(ApiRoutes.Users.EXPORT) { bearerAuth(bob.token) }.bodyAsText()
        ).jsonObject
        assertEquals("bob@example.com", bobs.getValue("profile").jsonObject.string("email"))
        assertEquals("PAID", bobs.array("settlements").single().string("direction"))
        assertFalse(bobs.array("expenses").single().getValue("paidByYou").jsonPrimitive.boolean)
    }

    @Test
    fun `the export needs a session`() = testApplication {
        useTestConfig(database)
        assertEquals(HttpStatusCode.Unauthorized, jsonClient().get(ApiRoutes.Users.EXPORT).status)
    }

    @Test
    fun `a profile photo only accepts the caller's own avatar keys and is served as a URL everywhere`() = testApplication {
        useStorage()
        val client = jsonClient()
        val ada = client.register("ada")
        val bob = client.register("bob")
        val group = client.createGroup(ada.token).withMember(client, ada.token, bob)

        val adaKey = client.avatarSlot(ada.token).body<UploadSlot>().objectKey
        val bobKey = client.avatarSlot(bob.token).body<UploadSlot>().objectKey
        assertTrue(adaKey.startsWith("users/${ada.id}/avatars/"))

        // Someone else's avatar, a group upload, or an arbitrary URL are all refused.
        assertEquals(HttpStatusCode.BadRequest, client.setAvatar(ada.token, bobKey).status)
        assertEquals(HttpStatusCode.BadRequest, client.setAvatar(ada.token, "https://evil.example/pixel.png").status)
        val coverKey = client.post(ApiRoutes.Groups.uploads(group.id.value)) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(UploadSlotRequest(UploadPurpose.GROUP_COVER, "image/jpeg", 1_000))
        }.body<UploadSlot>().objectKey
        assertEquals(HttpStatusCode.BadRequest, client.setAvatar(ada.token, coverKey).status)
        // ...and avatar slots are not handed out per group.
        val groupAvatar = client.post(ApiRoutes.Groups.uploads(group.id.value)) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(UploadSlotRequest(UploadPurpose.AVATAR, "image/jpeg", 1_000))
        }
        assertEquals(HttpStatusCode.BadRequest, groupAvatar.status)

        val updated = client.setAvatar(ada.token, adaKey)
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("https://cdn.splitfy.test/$adaKey", updated.body<User>().avatarUrl)

        // Bob sees Ada's photo on the group's member list.
        val seenByBob = client.get(ApiRoutes.Groups.byId(group.id.value)) { bearerAuth(bob.token) }.body<Group>()
        assertEquals("https://cdn.splitfy.test/$adaKey", seenByBob.members.first { it.userId.value == ada.id }.avatarUrl)

        // An empty string removes it.
        assertNull(client.setAvatar(ada.token, "").body<User>().avatarUrl)
    }

    @Test
    fun `the default currency can be changed and is validated`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada")

        val updated = client.patch(ApiRoutes.Users.ME) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(defaultCurrency = CurrencyCode("USD")))
        }
        assertEquals(CurrencyCode("USD"), updated.body<User>().defaultCurrency)

        val invalid = client.patch(ApiRoutes.Users.ME) {
            bearerAuth(ada.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(defaultCurrency = CurrencyCode("dollars")))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
    }

    private data class Account(val id: String, val token: String)

    private fun ApplicationTestBuilder.useStorage() = useTestConfig(
        database,
        extraConfig = mapOf(
            "storage.endpoint" to "https://acct.r2.cloudflarestorage.com",
            "storage.bucket" to "splitfy",
            "storage.accessKey" to "test-access-key",
            "storage.secretKey" to "test-secret-key",
            "storage.publicBaseUrl" to "https://cdn.splitfy.test",
        ),
    )

    private suspend fun HttpClient.register(name: String): Account {
        val auth = post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("$name@example.com", "Password123", name))
        }.body<AuthResponse>()
        return Account(auth.user.id.value, auth.tokens.accessToken)
    }

    private suspend fun HttpClient.createGroup(token: String, name: String = "Flat"): Group = post(ApiRoutes.Groups.BASE) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(CreateGroupRequest(name = name, currency = CurrencyCode.NGN))
    }.body()

    private suspend fun Group.withMember(client: HttpClient, ownerToken: String, member: Account): Group =
        client.post(ApiRoutes.Groups.members(id.value)) {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = UserId(member.id)))
        }.body<Group>().also { assertNotNull(it.memberOrNull(UserId(member.id))) }

    private suspend fun HttpClient.avatarSlot(token: String): HttpResponse = post(ApiRoutes.Users.ME_UPLOADS) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(UploadSlotRequest(UploadPurpose.AVATAR, "image/jpeg", 40_000))
    }

    private suspend fun HttpClient.setAvatar(token: String, key: String): HttpResponse = patch(ApiRoutes.Users.ME) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(UpdateProfileRequest(avatarUrl = key))
    }

    private fun JsonObject.array(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
}
