package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.ApiErrorResponse
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AddMemberRequest
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.FileLink
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.SplitPayload
import com.elozino.splitfy.api.dto.UpdateExpenseRequest
import com.elozino.splitfy.api.dto.UpdateGroupRequest
import com.elozino.splitfy.api.dto.UploadConfig
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadSlot
import com.elozino.splitfy.api.dto.UploadSlotRequest
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.UserId
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Upload slots, receipts and cover photos.
 *
 * Presigning is pure computation, so storage is "configured" with dummy credentials
 * and nothing ever talks to a real bucket.
 */
class UploadsTest {

    private val database = TestDatabase("splitfy_uploads")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `without storage configured uploads report disabled and slots are unavailable`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val owner = client.register("owner")
        val stranger = client.register("stranger")
        val group = client.createGroup(owner.token)

        assertFalse(client.get(ApiRoutes.Uploads.CONFIG) { bearerAuth(owner.token) }.body<UploadConfig>().enabled)

        val response = client.requestSlot(group, owner.token, UploadPurpose.RECEIPT)
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(ApiErrorCode.STORAGE_UNAVAILABLE, response.body<ApiErrorResponse>().code)
        // Outsiders still learn nothing about the group.
        assertEquals(HttpStatusCode.NotFound, client.requestSlot(group, stranger.token, UploadPurpose.RECEIPT).status)
    }

    @Test
    fun `members get receipt slots, only admins get cover slots, and outsiders get nothing`() = testApplication {
        useStorage()
        val client = jsonClient()
        val owner = client.register("owner")
        val member = client.register("member")
        val stranger = client.register("stranger")
        val group = client.createGroup(owner.token).withMember(client, owner.token, member)

        assertTrue(client.get(ApiRoutes.Uploads.CONFIG) { bearerAuth(owner.token) }.body<UploadConfig>().enabled)

        val response = client.requestSlot(group, member.token, UploadPurpose.RECEIPT)
        assertEquals(HttpStatusCode.Created, response.status)
        val slot = response.body<UploadSlot>()
        assertTrue(slot.objectKey.startsWith("groups/${group.id.value}/receipts/"), slot.objectKey)
        assertTrue(slot.objectKey.endsWith(".jpg"))
        assertTrue(slot.uploadUrl.startsWith("https://acct.r2.cloudflarestorage.com/splitfy/${slot.objectKey}?"))
        assertTrue("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost" in slot.uploadUrl)
        assertEquals(mapOf("Content-Type" to "image/jpeg"), slot.headers)
        // Keys are random, so a second slot never collides with or reveals the first.
        val second = client.requestSlot(group, member.token, UploadPurpose.RECEIPT).body<UploadSlot>()
        assertTrue(second.objectKey != slot.objectKey)

        assertEquals(HttpStatusCode.NotFound, client.requestSlot(group, stranger.token, UploadPurpose.RECEIPT).status)
        assertEquals(HttpStatusCode.Forbidden, client.requestSlot(group, member.token, UploadPurpose.GROUP_COVER).status)
        assertEquals(HttpStatusCode.Created, client.requestSlot(group, owner.token, UploadPurpose.GROUP_COVER).status)
    }

    @Test
    fun `slots are refused for disallowed types and sizes`() = testApplication {
        useStorage()
        val client = jsonClient()
        val owner = client.register("owner")
        val group = client.createGroup(owner.token)

        suspend fun assertRejected(response: HttpResponse, field: String) {
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(field, response.body<ApiErrorResponse>().fieldErrors.single().field)
        }

        assertRejected(client.requestSlot(group, owner.token, UploadPurpose.RECEIPT, "text/html"), "contentType")
        assertRejected(client.requestSlot(group, owner.token, UploadPurpose.GROUP_COVER, "application/pdf"), "contentType")
        assertRejected(client.requestSlot(group, owner.token, UploadPurpose.RECEIPT, size = 0), "sizeBytes")
        assertRejected(client.requestSlot(group, owner.token, UploadPurpose.RECEIPT, size = 10L * 1024 * 1024 + 1), "sizeBytes")

        assertEquals(
            HttpStatusCode.Created,
            client.requestSlot(group, owner.token, UploadPurpose.RECEIPT, "application/pdf", 10L * 1024 * 1024).status,
        )
        assertEquals(
            HttpStatusCode.Created,
            client.requestSlot(group, owner.token, UploadPurpose.RECEIPT, "IMAGE/HEIC").status,
        )
    }

    @Test
    fun `an expense only accepts receipt keys issued for its own group`() = testApplication {
        useStorage()
        val client = jsonClient()
        val owner = client.register("owner")
        val group = client.createGroup(owner.token)
        val otherGroup = client.createGroup(owner.token, "Other")

        val ours = client.requestSlot(group, owner.token, UploadPurpose.RECEIPT).body<UploadSlot>().objectKey
        val theirs = client.requestSlot(otherGroup, owner.token, UploadPurpose.RECEIPT).body<UploadSlot>().objectKey
        val cover = client.requestSlot(group, owner.token, UploadPurpose.GROUP_COVER).body<UploadSlot>().objectKey

        for (rejected in listOf(
            "https://evil.example/receipt.jpg",
            theirs,
            cover,
            "groups/${group.id.value}/receipts/made-up.jpg",
        )) {
            val response = client.createExpense(group, owner, receiptUrl = rejected)
            assertEquals(HttpStatusCode.BadRequest, response.status, rejected)
            assertEquals("receiptUrl", response.body<ApiErrorResponse>().fieldErrors.single().field)
        }

        val created = client.createExpense(group, owner, receiptUrl = ours)
        assertEquals(HttpStatusCode.Created, created.status)
        val expense = created.body<Expense>()
        assertEquals(ours, expense.receiptUrl)

        // Editing follows the same rule, and an empty string removes the receipt.
        val byId = ApiRoutes.Expenses.byId(group.id.value, expense.id.value)
        assertEquals(HttpStatusCode.BadRequest, client.patchJson(byId, owner.token, UpdateExpenseRequest(receiptUrl = theirs)).status)
        val cleared = client.patchJson(byId, owner.token, UpdateExpenseRequest(receiptUrl = "")).body<Expense>()
        assertNull(cleared.receiptUrl)
    }

    @Test
    fun `receipts are readable by members only, through short-lived signed links`() = testApplication {
        useStorage()
        val client = jsonClient()
        val owner = client.register("owner")
        val member = client.register("member")
        val stranger = client.register("stranger")
        val group = client.createGroup(owner.token).withMember(client, owner.token, member)

        val key = client.requestSlot(group, owner.token, UploadPurpose.RECEIPT).body<UploadSlot>().objectKey
        val expense = client.createExpense(group, owner, receiptUrl = key).body<Expense>()
        val receipt = ApiRoutes.Expenses.receipt(group.id.value, expense.id.value)

        val response = client.get(receipt) { bearerAuth(member.token) }
        assertEquals(HttpStatusCode.OK, response.status)
        val link = response.body<FileLink>()
        assertTrue(link.url.startsWith("https://acct.r2.cloudflarestorage.com/splitfy/$key?"))
        assertTrue("X-Amz-Expires=300" in link.url)

        assertEquals(HttpStatusCode.NotFound, client.get(receipt) { bearerAuth(stranger.token) }.status)

        val plain = client.createExpense(group, owner).body<Expense>()
        assertEquals(
            HttpStatusCode.NotFound,
            client.get(ApiRoutes.Expenses.receipt(group.id.value, plain.id.value)) { bearerAuth(owner.token) }.status,
        )
    }

    @Test
    fun `a group cover only accepts its own cover keys and is served as a URL`() = testApplication {
        useStorage(publicBaseUrl = "https://cdn.splitfy.app")
        val client = jsonClient()
        val owner = client.register("owner")
        val group = client.createGroup(owner.token)
        val byId = ApiRoutes.Groups.byId(group.id.value)

        val created = client.post(ApiRoutes.Groups.BASE) {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest("Trip", currency = CurrencyCode.NGN, coverImageUrl = "https://evil.example/x.png"))
        }
        assertEquals(HttpStatusCode.BadRequest, created.status)

        val receiptKey = client.requestSlot(group, owner.token, UploadPurpose.RECEIPT).body<UploadSlot>().objectKey
        for (rejected in listOf("https://evil.example/x.png", receiptKey)) {
            assertEquals(HttpStatusCode.BadRequest, client.patchJson(byId, owner.token, UpdateGroupRequest(coverImageUrl = rejected)).status)
        }

        val key = client.requestSlot(group, owner.token, UploadPurpose.GROUP_COVER).body<UploadSlot>().objectKey
        val updated = client.patchJson(byId, owner.token, UpdateGroupRequest(coverImageUrl = key)).body<Group>()
        assertEquals("https://cdn.splitfy.app/$key", updated.coverImageUrl)
        assertEquals(updated.coverImageUrl, client.get(byId) { bearerAuth(owner.token) }.body<Group>().coverImageUrl)

        val cleared = client.patchJson(byId, owner.token, UpdateGroupRequest(coverImageUrl = "")).body<Group>()
        assertNull(cleared.coverImageUrl)
    }

    @Test
    fun `without a public host covers are signed links`() = testApplication {
        useStorage()
        val client = jsonClient()
        val owner = client.register("owner")
        val group = client.createGroup(owner.token)

        val key = client.requestSlot(group, owner.token, UploadPurpose.GROUP_COVER).body<UploadSlot>().objectKey
        val updated = client.patchJson(ApiRoutes.Groups.byId(group.id.value), owner.token, UpdateGroupRequest(coverImageUrl = key))
            .body<Group>()

        val url = assertNotNull(updated.coverImageUrl)
        assertTrue(url.startsWith("https://acct.r2.cloudflarestorage.com/splitfy/$key?"))
        assertTrue("X-Amz-Signature=" in url)
    }

    // --- helpers ---------------------------------------------------------------

    private data class Account(val id: String, val token: String)

    private fun ApplicationTestBuilder.useStorage(publicBaseUrl: String = "") = useTestConfig(
        database,
        extraConfig = mapOf(
            "storage.endpoint" to "https://acct.r2.cloudflarestorage.com",
            "storage.bucket" to "splitfy",
            "storage.accessKey" to "test-access-key",
            "storage.secretKey" to "test-secret-key",
            "storage.publicBaseUrl" to publicBaseUrl,
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
        }.body()

    private suspend fun HttpClient.requestSlot(
        group: Group,
        token: String,
        purpose: UploadPurpose,
        contentType: String = "image/jpeg",
        size: Long = 120_000,
    ): HttpResponse = post(ApiRoutes.Groups.uploads(group.id.value)) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(UploadSlotRequest(purpose, contentType, size))
    }

    private suspend fun HttpClient.createExpense(group: Group, payer: Account, receiptUrl: String? = null): HttpResponse =
        post(ApiRoutes.Expenses.forGroup(group.id.value)) {
            bearerAuth(payer.token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateExpenseRequest(
                    description = "Groceries",
                    total = Money(5_000, CurrencyCode.NGN),
                    paidBy = UserId(payer.id),
                    split = SplitPayload(SplitMethod.EQUAL, listOf(UserId(payer.id))),
                    receiptUrl = receiptUrl,
                )
            )
        }

    private suspend inline fun <reified T : Any> HttpClient.patchJson(path: String, token: String, body: T): HttpResponse =
        patch(path) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
}
