package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiErrorResponse
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.api.dto.DevicePlatform
import com.elozino.splitfy.api.dto.GroupBalancesResponse
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.api.dto.RegisterDeviceRequest
import com.elozino.splitfy.api.dto.RefreshTokenRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.SplitPayload
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupRole
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SplitMethod
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionTest {

    private val database = TestDatabase("splitfy_account_deletion")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `deletion is refused while a group balance is outstanding and names the group`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val bode = client.register("bode@example.com")
        val group = client.groupWithDinner(ada, bode, name = "Lagos trip")

        val refused = client.delete(ApiRoutes.Users.ME) { bearerAuth(bode.tokens.accessToken) }
        assertEquals(HttpStatusCode.Conflict, refused.status)
        val message = refused.body<ApiErrorResponse>().message
        assertTrue("Lagos trip" in message, message)

        // Nothing changed: Bode can still sign in.
        assertEquals(HttpStatusCode.OK, client.login("bode@example.com").status)
        assertEquals(HttpStatusCode.OK, client.get(ApiRoutes.Groups.byId(group.id.value)) { bearerAuth(bode.tokens.accessToken) }.status)
    }

    @Test
    fun `a settled user is anonymised and group history is unchanged`() = testApplication {
        useTestConfig(database)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val bode = client.register("bode@example.com")
        val group = client.groupWithDinner(ada, bode, name = "Flat")

        // Bode pays Ada back and Ada confirms, so both balances are zero.
        val settlement = client.post(ApiRoutes.Settlements.forGroup(group.id.value)) {
            bearerAuth(bode.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateSettlementRequest(bode.user.id, ada.user.id, Money(5_000, CurrencyCode.NGN)))
        }.body<Settlement>()
        client.post(ApiRoutes.Settlements.confirm(group.id.value, settlement.id.value)) { bearerAuth(ada.tokens.accessToken) }
        val before = client.balances(group, ada)
        client.post(ApiRoutes.Devices.BASE) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest("ada-phone", DevicePlatform.ANDROID))
        }

        // Ada owns the group; deleting hands ownership to Bode.
        val deleted = client.delete(ApiRoutes.Users.ME) { bearerAuth(ada.tokens.accessToken) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        // Credentials and sessions are gone.
        assertEquals(HttpStatusCode.Unauthorized, client.login("ada@example.com").status)
        val refresh = client.post(ApiRoutes.Auth.REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(ada.tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, refresh.status)

        // History and balances are intact; Ada now shows as "Deleted user".
        val after = client.balances(group, bode)
        assertEquals(before.balances, after.balances)
        val reloaded = client.get(ApiRoutes.Groups.byId(group.id.value)) { bearerAuth(bode.tokens.accessToken) }.body<Group>()
        val adaMember = reloaded.members.single { it.userId == ada.user.id }
        assertEquals("Deleted user", adaMember.displayName)
        assertEquals(GroupRole.OWNER, reloaded.members.single { it.userId == bode.user.id }.role)

        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { conn ->
            conn.prepareStatement("SELECT email, phone_number, deleted_at FROM users WHERE id = ?::uuid").use { st ->
                st.setString(1, ada.user.id.value)
                st.executeQuery().use {
                    assertTrue(it.next())
                    assertTrue(it.getString(1).endsWith("@deleted.invalid"))
                    assertNull(it.getString(2))
                    assertNotNull(it.getTimestamp(3))
                }
            }
            // The user row survives, so these are cleared explicitly rather than by cascade.
            for (table in listOf("refresh_tokens", "device_tokens")) {
                conn.prepareStatement("SELECT count(*) FROM $table WHERE user_id = ?::uuid").use { st ->
                    st.setString(1, ada.user.id.value)
                    st.executeQuery().use { it.next(); assertEquals(0, it.getInt(1), table) }
                }
            }
        }

        // The address is free to sign up again, as a brand-new account.
        val again = client.register("ada@example.com")
        assertTrue(again.user.id != ada.user.id)
    }

    private suspend fun HttpClient.register(email: String): AuthResponse = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, "Password123", email.substringBefore('@')))
    }.also { assertEquals(HttpStatusCode.Created, it.status) }.body()

    private suspend fun HttpClient.login(email: String) = post(ApiRoutes.Auth.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, "Password123"))
    }

    /** A group owned by [owner] with [friend] in it, and a 100.00 dinner [owner] paid for both. */
    private suspend fun HttpClient.groupWithDinner(owner: AuthResponse, friend: AuthResponse, name: String): Group {
        val group = post(ApiRoutes.Groups.BASE) {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = name, currency = CurrencyCode.NGN, memberIds = listOf(friend.user.id)))
        }.body<Group>()
        post(ApiRoutes.Expenses.forGroup(group.id.value)) {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateExpenseRequest(
                    description = "Dinner",
                    total = Money(10_000, CurrencyCode.NGN),
                    paidBy = owner.user.id,
                    split = SplitPayload(SplitMethod.EQUAL, participantIds = listOf(owner.user.id, friend.user.id)),
                ),
            )
        }.also { assertEquals(HttpStatusCode.Created, it.status) }
        return group
    }

    private suspend fun HttpClient.balances(group: Group, caller: AuthResponse) =
        get(ApiRoutes.Groups.balances(group.id.value)) { bearerAuth(caller.tokens.accessToken) }.body<GroupBalancesResponse>()
}
