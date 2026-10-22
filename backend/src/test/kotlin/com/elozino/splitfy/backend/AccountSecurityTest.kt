package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.ApiErrorResponse
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.ChangePasswordRequest
import com.elozino.splitfy.api.dto.DevicePlatform
import com.elozino.splitfy.api.dto.DeviceSession
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.api.dto.RecoveryCodesResponse
import com.elozino.splitfy.api.dto.RefreshTokenRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.RevokedSessionsResponse
import com.elozino.splitfy.api.dto.SecurityOverview
import com.elozino.splitfy.api.dto.SignInResponse
import com.elozino.splitfy.api.dto.TokenPair
import com.elozino.splitfy.api.dto.TwoFactorDisableRequest
import com.elozino.splitfy.api.dto.TwoFactorEnableRequest
import com.elozino.splitfy.api.dto.TwoFactorSetupResponse
import com.elozino.splitfy.api.dto.TwoFactorVerifyRequest
import com.elozino.splitfy.backend.services.TwoFactorService
import com.elozino.splitfy.backend.utils.Base32
import com.elozino.splitfy.backend.utils.SecretCipher
import com.elozino.splitfy.backend.utils.Totp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Password change, device sessions and authenticator-app two-step verification.
 *
 * Runs as STAGING so X-Forwarded-For is honoured, and every request comes from a new
 * address: the per-IP buckets on the auth endpoints never trip, so what these tests
 * see is the account-level behaviour (lockouts, revocation), not the rate limiter.
 */
class AccountSecurityTest {

    private val database = TestDatabase("splitfy_security")

    /** Frozen, so a test controls exactly which 30-second step each code belongs to. */
    private class TestClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private val clock = TestClock(Clock.System.now())
    private val overrides = listOf(
        module {
            single { TwoFactorService(get(), get(), get(), SecretCipher("test-totp-key-that-is-long-enough-123"), clock) }
        },
    )

    @BeforeTest
    fun setUp() = database.reset()

    // --- Password -------------------------------------------------------------

    @Test
    fun `a wrong current password is a 400, not a 401`() = securityTest {
        val ada = register("ada@example.com")

        val response = changePassword(ada.tokens.accessToken, current = "WrongPassword1", new = "NewPassword456")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ApiErrorResponse>()
        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, error.code)
        assertEquals("currentPassword", error.fieldErrors.single().field)
    }

    @Test
    fun `the new password must pass the same rules as sign-up`() = securityTest {
        val ada = register("ada@example.com")

        val response = changePassword(ada.tokens.accessToken, current = PASSWORD, new = "short")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("newPassword", response.body<ApiErrorResponse>().fieldErrors.single().field)
    }

    @Test
    fun `changing the password signs out other devices and keeps this one`() = securityTest {
        val phone = register("ada@example.com", device = "Ada's iPhone")
        val laptop = login("ada@example.com", device = "Ada's Pixel").tokensOrFail()

        val response = changePassword(phone.tokens.accessToken, current = PASSWORD, new = "NewPassword456")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(laptop.refreshToken).status, "the other device is signed out")
        assertEquals(HttpStatusCode.OK, refresh(phone.tokens.refreshToken).status, "this device stays signed in")
        assertEquals(HttpStatusCode.Unauthorized, loginResponse("ada@example.com", PASSWORD).status)
        assertNotNull(login("ada@example.com", password = "NewPassword456").tokens)
    }

    @Test
    fun `an account without a usable password can set one without the current password`() = securityTest {
        val ada = register("ada@example.com")
        database.execute("UPDATE users SET has_password = FALSE WHERE email = 'ada@example.com'")
        assertFalse(overview(ada.tokens.accessToken).hasPassword)

        val response = changePassword(ada.tokens.accessToken, current = null, new = "ChosenPassword1")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(overview(ada.tokens.accessToken).hasPassword)
        assertNotNull(login("ada@example.com", password = "ChosenPassword1").tokens)
        // From now on the current password is required.
        assertEquals(HttpStatusCode.BadRequest, changePassword(ada.tokens.accessToken, current = null, new = "Another123").status)
    }

    // --- Sessions -------------------------------------------------------------

    @Test
    fun `sessions list each device with its name and mark the caller`() = securityTest {
        register("ada@example.com", device = "Ada's iPhone", platform = "ios")
        val pixel = login("ada@example.com", device = "Ada's Pixel", platform = "ANDROID").tokensOrFail()

        val response = client.get(ApiRoutes.Security.SESSIONS) { bearerAuth(pixel.accessToken) }
        val sessions = response.body<List<DeviceSession>>()

        assertEquals(2, sessions.size)
        val current = sessions.single { it.current }
        assertEquals("Ada's Pixel", current.deviceName)
        assertEquals(DevicePlatform.ANDROID, current.platform)
        val other = sessions.single { !it.current }
        assertEquals("Ada's iPhone", other.deviceName)
        assertEquals(DevicePlatform.IOS, other.platform)
        val raw = response.bodyAsText()
        assertFalse("token" in raw.lowercase() || "hash" in raw.lowercase(), "Sessions must not expose tokens: $raw")
    }

    @Test
    fun `a refresh keeps the device in the same session`() = securityTest {
        val ada = register("ada@example.com", device = "Ada's iPhone")

        val rotated = refresh(ada.tokens.refreshToken).body<TokenPair>()
        val sessions = client.get(ApiRoutes.Security.SESSIONS) { bearerAuth(rotated.accessToken) }.body<List<DeviceSession>>()

        assertEquals(1, sessions.size)
        assertTrue(sessions.single().current)
        assertTrue(sessions.single().lastUsedAt >= sessions.single().createdAt)
    }

    @Test
    fun `revoking one device signs only that device out`() = securityTest {
        val phone = register("ada@example.com", device = "Phone")
        val tablet = login("ada@example.com", device = "Tablet").tokensOrFail()
        val laptop = login("ada@example.com", device = "Laptop").tokensOrFail()
        val tabletId = sessions(phone.tokens.accessToken).single { it.deviceName == "Tablet" }.id

        val response = client.delete(ApiRoutes.Security.session(tabletId)) { bearerAuth(phone.tokens.accessToken) }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(tablet.refreshToken).status)
        // The revoked device trying again must not read as token theft and sign everyone out.
        assertEquals(HttpStatusCode.OK, refresh(laptop.refreshToken).status)
        assertEquals(HttpStatusCode.OK, refresh(phone.tokens.refreshToken).status)
        assertEquals(setOf("Phone", "Laptop"), sessions(phone.tokens.accessToken).map { it.deviceName }.toSet())
    }

    @Test
    fun `the caller cannot revoke its own session and others' ids are not found`() = securityTest {
        val ada = register("ada@example.com")
        val bode = register("bode@example.com")
        val own = sessions(ada.tokens.accessToken).single().id
        val bodes = sessions(bode.tokens.accessToken).single().id

        assertEquals(HttpStatusCode.BadRequest, client.delete(ApiRoutes.Security.session(own)) { bearerAuth(ada.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete(ApiRoutes.Security.session(bodes)) { bearerAuth(ada.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete(ApiRoutes.Security.session("not-a-uuid")) { bearerAuth(ada.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.OK, refresh(bode.tokens.refreshToken).status)
    }

    @Test
    fun `signing out of all other devices keeps only this one`() = securityTest {
        val phone = register("ada@example.com", device = "Phone")
        val tablet = login("ada@example.com", device = "Tablet").tokensOrFail()
        login("ada@example.com", device = "Laptop").tokensOrFail()

        val response = client.post(ApiRoutes.Security.REVOKE_OTHER_SESSIONS) { bearerAuth(phone.tokens.accessToken) }

        assertEquals(2, response.body<RevokedSessionsResponse>().revoked)
        assertEquals(listOf("Phone"), sessions(phone.tokens.accessToken).map { it.deviceName })
        assertEquals(HttpStatusCode.Unauthorized, refresh(tablet.refreshToken).status)
        assertEquals(HttpStatusCode.OK, refresh(phone.tokens.refreshToken).status)
    }

    @Test
    fun `signing out removes the device from the list`() = securityTest {
        val phone = register("ada@example.com", device = "Phone")
        val tablet = login("ada@example.com", device = "Tablet").tokensOrFail()

        client.post(ApiRoutes.Auth.LOGOUT) {
            contentType(ContentType.Application.Json)
            setBody(com.elozino.splitfy.api.dto.LogoutRequest(tablet.refreshToken))
        }

        assertEquals(listOf("Phone"), sessions(phone.tokens.accessToken).map { it.deviceName })
    }

    // --- Two-step verification ------------------------------------------------

    @Test
    fun `enrolment needs a verified email`() = securityTest {
        val ada = register("ada@example.com")

        val response = client.post(ApiRoutes.Security.TWO_FACTOR_SETUP) { bearerAuth(ada.tokens.accessToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `enrolment only takes effect once a code proves the app has the secret`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val setup = setup(ada)
        assertTrue(setup.otpauthUri.startsWith("otpauth://totp/Splitfy:ada%40example.com?secret=${setup.secret}"))

        // Pending: sign-in still issues tokens straight away.
        assertNotNull(login("ada@example.com").tokens)

        val wrong = enable(ada, "000000".takeUnless { it == codeAt(setup, 0) } ?: "111111")
        assertEquals(HttpStatusCode.BadRequest, wrong.status)
        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, wrong.body<ApiErrorResponse>().code)

        val codes = enable(ada, codeAt(setup, 0)).body<RecoveryCodesResponse>().recoveryCodes

        assertEquals(10, codes.size)
        assertEquals(10, codes.toSet().size)
        assertEquals(SecurityOverview(hasPassword = true, twoFactorEnabled = true, recoveryCodesRemaining = 10), overview(ada.tokens.accessToken))
        assertEquals(HttpStatusCode.Conflict, client.post(ApiRoutes.Security.TWO_FACTOR_SETUP) { bearerAuth(ada.tokens.accessToken) }.status)
    }

    @Test
    fun `recovery codes are stored hashed`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (_, codes) = enrol(ada)

        val stored = database.query("SELECT code_hash FROM totp_recovery_codes")
        assertEquals(10, stored.size)
        assertTrue(codes.none { code -> stored.any { it.contains(code.replace("-", "")) } })
        val secret = database.query("SELECT secret_encrypted FROM user_totp").single()
        assertFalse(secret.contains(setupSecretOf(ada)), "TOTP secret must be encrypted")
    }

    @Test
    fun `with 2FA on, a password alone gets a challenge and the code gets the tokens`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (setup, _) = enrol(ada)

        val first = login("ada@example.com", device = "New phone")
        assertNull(first.tokens)
        assertNull(first.user)
        val challenge = assertNotNull(first.twoFactor)

        nextStep()
        val response = verify(challenge.challengeToken, codeAt(setup, 0), device = "New phone")

        assertEquals(HttpStatusCode.OK, response.status)
        val signedIn = response.body<AuthResponse>()
        assertEquals("ada@example.com", signedIn.user.email)
        val names = sessions(signedIn.tokens.accessToken).map { it.deviceName }
        assertTrue("New phone" in names, "the session is named after the device that finished sign-in: $names")
        // The challenge is single use.
        assertEquals(HttpStatusCode.Unauthorized, verify(challenge.challengeToken, codeAt(setup, 0)).status)
    }

    @Test
    fun `a code is never accepted twice`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (setup, _) = enrol(ada)
        nextStep()
        val code = codeAt(setup, 0)

        assertEquals(HttpStatusCode.OK, verify(login("ada@example.com").twoFactor!!.challengeToken, code).status)
        val replay = verify(login("ada@example.com").twoFactor!!.challengeToken, code)

        assertEquals(HttpStatusCode.Unauthorized, replay.status)
        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, replay.body<ApiErrorResponse>().code)
    }

    @Test
    fun `codes from one step either side are accepted, two steps are not`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (setup, _) = enrol(ada)
        repeat(3) { nextStep() }

        assertEquals(HttpStatusCode.Unauthorized, verify(login("ada@example.com").twoFactor!!.challengeToken, codeAt(setup, 2)).status)
        assertEquals(HttpStatusCode.OK, verify(login("ada@example.com").twoFactor!!.challengeToken, codeAt(setup, -1)).status)
        assertEquals(HttpStatusCode.OK, verify(login("ada@example.com").twoFactor!!.challengeToken, codeAt(setup, 1)).status)
    }

    @Test
    fun `a recovery code works once`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (_, codes) = enrol(ada)

        // Typed loosely: upper case, no dash, stray spaces.
        val typed = " " + codes.first().uppercase().replace("-", " ") + " "
        assertEquals(HttpStatusCode.OK, verify(login("ada@example.com").twoFactor!!.challengeToken, typed).status)
        assertEquals(HttpStatusCode.Unauthorized, verify(login("ada@example.com").twoFactor!!.challengeToken, codes.first()).status)
        assertEquals(9, overview(ada.tokens.accessToken).recoveryCodesRemaining)
    }

    @Test
    fun `five wrong codes lock code entry for the account even from different addresses`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (setup, _) = enrol(ada)
        nextStep()
        val right = codeAt(setup, 0)
        val wrong = if (right == "123456") "654321" else "123456"

        repeat(5) {
            val attempt = verify(login("ada@example.com").twoFactor!!.challengeToken, wrong)
            assertEquals(HttpStatusCode.Unauthorized, attempt.status)
        }
        val locked = verify(login("ada@example.com").twoFactor!!.challengeToken, right)

        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals(ApiErrorCode.RATE_LIMITED, locked.body<ApiErrorResponse>().code)
        // Disabling is locked too, so the lockout cannot be sidestepped from a stolen session.
        assertEquals(HttpStatusCode.TooManyRequests, disable(ada, code = right).status)

        clock.now += TwoFactorService.LOCKOUT + 1.seconds
        assertEquals(HttpStatusCode.OK, verify(login("ada@example.com").twoFactor!!.challengeToken, codeAt(setup, 0)).status)
    }

    @Test
    fun `a challenge expires after five minutes`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (setup, _) = enrol(ada)
        nextStep()
        val expiring = login("ada@example.com").twoFactor!!.challengeToken

        clock.now += TwoFactorService.CHALLENGE_TTL + 1.seconds

        assertEquals(HttpStatusCode.Unauthorized, verify(expiring, codeAt(setup, 0)).status)
    }

    @Test
    fun `disabling needs a current code or the password`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (setup, _) = enrol(ada)

        assertEquals(HttpStatusCode.BadRequest, disable(ada).status)
        assertEquals(HttpStatusCode.BadRequest, disable(ada, password = "WrongPassword1").status)
        nextStep()
        val code = codeAt(setup, 0)
        assertEquals(HttpStatusCode.BadRequest, disable(ada, code = if (code == "000000") "111111" else "000000").status)

        assertEquals(HttpStatusCode.OK, disable(ada, password = PASSWORD).status)

        assertFalse(overview(ada.tokens.accessToken).twoFactorEnabled)
        assertNotNull(login("ada@example.com").tokens, "sign-in is back to one step")
        assertTrue(database.query("SELECT code_hash FROM totp_recovery_codes").isEmpty())
    }

    @Test
    fun `disabling with an authenticator code works`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        val (setup, _) = enrol(ada)
        nextStep()

        assertEquals(HttpStatusCode.OK, disable(ada, code = codeAt(setup, 0)).status)
        assertFalse(overview(ada.tokens.accessToken).twoFactorEnabled)
    }

    @Test
    fun `deleting the account removes sessions and the authenticator`() = securityTest {
        val ada = verifiedAccount("ada@example.com")
        enrol(ada)

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiRoutes.Users.ME) { bearerAuth(ada.tokens.accessToken) }.status)

        assertTrue(database.query("SELECT id::text FROM auth_sessions").isEmpty())
        assertTrue(database.query("SELECT user_id::text FROM user_totp").isEmpty())
        assertTrue(database.query("SELECT id::text FROM totp_recovery_codes").isEmpty())
    }

    // --- Harness --------------------------------------------------------------

    private class Scope(val client: HttpClient)

    private fun securityTest(block: suspend Scope.() -> Unit) = testApplication {
        useTestConfig(database, overrides = overrides, environment = "STAGING")
        Scope(addressRotatingClient()).block()
    }

    private val addresses = AtomicInteger()

    private fun ApplicationTestBuilder.addressRotatingClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        install(
            createClientPlugin("NewAddressPerRequest") {
                onRequest { request, _ ->
                    val n = addresses.incrementAndGet()
                    request.headers.append("X-Forwarded-For", "10.${n / 65536 % 256}.${n / 256 % 256}.${n % 256}")
                }
            },
        )
    }

    private val secrets = mutableMapOf<String, String>()

    private fun setupSecretOf(account: AuthResponse): String = secrets.getValue(account.user.email)

    private fun nextStep() {
        clock.now += Totp.PERIOD_SECONDS.seconds
    }

    private fun codeAt(setup: TwoFactorSetupResponse, offset: Long): String =
        Totp.code(Base32.decode(setup.secret), Totp.step(clock.now.epochSeconds) + offset)

    private suspend fun Scope.register(email: String, device: String? = null, platform: String? = null): AuthResponse =
        client.post(ApiRoutes.Auth.REGISTER) {
            device?.let { header(ApiRoutes.Headers.DEVICE_NAME, it) }
            platform?.let { header(ApiRoutes.Headers.DEVICE_PLATFORM, it) }
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, PASSWORD, email.substringBefore('@')))
        }.body()

    private suspend fun Scope.verifiedAccount(email: String): AuthResponse =
        register(email).also { database.markVerified(email) }

    private suspend fun Scope.loginResponse(
        email: String,
        password: String = PASSWORD,
        device: String? = null,
        platform: String? = null,
    ): HttpResponse = client.post(ApiRoutes.Auth.LOGIN) {
        device?.let { header(ApiRoutes.Headers.DEVICE_NAME, it) }
        platform?.let { header(ApiRoutes.Headers.DEVICE_PLATFORM, it) }
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, password))
    }

    private suspend fun Scope.login(
        email: String,
        password: String = PASSWORD,
        device: String? = null,
        platform: String? = null,
    ): SignInResponse = loginResponse(email, password, device, platform).also {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
    }.body()

    private fun SignInResponse.tokensOrFail(): TokenPair = assertNotNull(tokens, "expected tokens, got $this")

    private suspend fun Scope.refresh(refreshToken: String): HttpResponse = client.post(ApiRoutes.Auth.REFRESH) {
        contentType(ContentType.Application.Json)
        setBody(RefreshTokenRequest(refreshToken))
    }

    private suspend fun Scope.changePassword(token: String, current: String?, new: String): HttpResponse =
        client.post(ApiRoutes.Security.PASSWORD) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(current, new))
        }

    private suspend fun Scope.overview(token: String): SecurityOverview =
        client.get(ApiRoutes.Security.BASE) { bearerAuth(token) }.body()

    private suspend fun Scope.sessions(token: String): List<DeviceSession> =
        client.get(ApiRoutes.Security.SESSIONS) { bearerAuth(token) }.body()

    private suspend fun Scope.setup(account: AuthResponse): TwoFactorSetupResponse =
        client.post(ApiRoutes.Security.TWO_FACTOR_SETUP) { bearerAuth(account.tokens.accessToken) }
            .also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
            .body<TwoFactorSetupResponse>()
            .also { secrets[account.user.email] = it.secret }

    private suspend fun Scope.enable(account: AuthResponse, code: String): HttpResponse =
        client.post(ApiRoutes.Security.TWO_FACTOR_ENABLE) {
            bearerAuth(account.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(TwoFactorEnableRequest(code))
        }

    /** Turns 2FA on; returns the setup (for codes) and the recovery codes. */
    private suspend fun Scope.enrol(account: AuthResponse): Pair<TwoFactorSetupResponse, List<String>> {
        val setup = setup(account)
        val response = enable(account, codeAt(setup, 0))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return setup to response.body<RecoveryCodesResponse>().recoveryCodes
    }

    private suspend fun Scope.verify(challengeToken: String, code: String, device: String? = null): HttpResponse =
        client.post(ApiRoutes.Auth.TWO_FACTOR_VERIFY) {
            device?.let { header(ApiRoutes.Headers.DEVICE_NAME, it) }
            contentType(ContentType.Application.Json)
            setBody(TwoFactorVerifyRequest(challengeToken, code))
        }

    private suspend fun Scope.disable(account: AuthResponse, code: String? = null, password: String? = null): HttpResponse =
        client.post(ApiRoutes.Security.TWO_FACTOR_DISABLE) {
            bearerAuth(account.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(TwoFactorDisableRequest(code, password))
        }

    private fun TestDatabase.execute(sql: String) {
        DriverManager.getConnection(jdbcUrl, username, password).use { it.createStatement().use { s -> s.executeUpdate(sql) } }
    }

    /** First column of every row, as text. */
    private fun TestDatabase.query(sql: String): List<String> =
        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    private companion object {
        const val PASSWORD = "Password123"
    }
}
