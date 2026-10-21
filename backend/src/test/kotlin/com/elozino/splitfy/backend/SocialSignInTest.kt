package com.elozino.splitfy.backend

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.api.dto.RefreshTokenRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.backend.services.IdentityTokenVerifier
import com.elozino.splitfy.api.dto.SecurityOverview
import com.elozino.splitfy.api.dto.SignInResponse
import com.elozino.splitfy.api.dto.TwoFactorEnableRequest
import com.elozino.splitfy.api.dto.TwoFactorSetupResponse
import com.elozino.splitfy.api.dto.TwoFactorVerifyRequest
import com.elozino.splitfy.backend.utils.Base32
import com.elozino.splitfy.backend.utils.Totp
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.test.assertFalse
import kotlin.test.assertNull
import com.elozino.splitfy.backend.services.SocialAuthService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.dsl.module

class SocialSignInTest {

    private val database = TestDatabase("splitfy_social")

    private val clientId = "test-client.apps.googleusercontent.com"
    private val issuer = "https://accounts.google.com"
    private val keyId = "test-key"

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey

    /** Serves our test key the way Google's JWKS endpoint would. */
    private val keys = JwkProvider { kid ->
        require(kid == keyId) { "unknown key $kid" }
        val b64 = Base64.getUrlEncoder().withoutPadding()
        Jwk.fromValues(
            mapOf(
                "kid" to keyId,
                "kty" to "RSA",
                "alg" to "RS256",
                "use" to "sig",
                "n" to b64.encodeToString(publicKey.modulus.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()),
                "e" to b64.encodeToString(publicKey.publicExponent.toByteArray()),
            )
        )
    }

    private val overrides = listOf(
        module {
            single {
                SocialAuthService(
                    get(), get(), get(), get(), get(), get(),
                    verifiers = listOf(IdentityTokenVerifier(OAuthProvider.GOOGLE, keys, setOf(issuer), setOf(clientId))),
                )
            }
        }
    )

    @BeforeTest
    fun setUp() = database.reset()

    private fun idToken(
        subject: String = "google-sub-1",
        email: String? = "ada@example.com",
        emailVerified: Boolean = true,
        audience: String = clientId,
        expiresInSeconds: Long = 3600,
        nonce: String? = null,
        signingKey: RSAPrivateKey = privateKey,
    ): String = JWT.create()
        .withKeyId(keyId)
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(subject)
        .withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + expiresInSeconds * 1000))
        .apply {
            email?.let { withClaim("email", it) }
            withClaim("email_verified", emailVerified)
            withClaim("name", "Ada Lovelace")
            nonce?.let { withClaim("nonce", it) }
        }
        .sign(Algorithm.RSA256(publicKey, signingKey))

    private suspend fun HttpClient.google(token: String, nonce: String? = null): HttpResponse =
        post(ApiRoutes.Auth.oauth("google")) {
            contentType(ContentType.Application.Json)
            setBody(OAuthSignInRequest(idToken = token, nonce = nonce))
        }

    @Test
    fun `a new Google user gets a verified account and keeps it on the next sign-in`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()

        val first = client.google(idToken()).body<AuthResponse>()
        val second = client.google(idToken()).body<AuthResponse>()

        assertTrue(first.user.emailVerified)
        assertEquals("Ada Lovelace", first.user.displayName)
        assertEquals(first.user.id, second.user.id)
    }

    @Test
    fun `tokens that are not ours are rejected`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val otherKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val forged = listOf(
            idToken(audience = "someone-elses-app"),
            idToken(expiresInSeconds = -3600),
            idToken(signingKey = otherKey.private as RSAPrivateKey),
        )

        forged.forEach { assertEquals(HttpStatusCode.Unauthorized, client.google(it).status) }
    }

    @Test
    fun `a nonce must match when one is sent`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()

        assertEquals(HttpStatusCode.Unauthorized, client.google(idToken(nonce = "abc"), nonce = "xyz").status)
        assertEquals(HttpStatusCode.OK, client.google(idToken(nonce = "abc"), nonce = "abc").status)
    }

    @Test
    fun `an unverified provider email is refused`() = testApplication {
        useTestConfig(database, overrides = overrides)

        val response = jsonClient().google(idToken(emailVerified = false))

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a verified password account is linked, and its password still works`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val registered = client.register("ada@example.com")
        database.markVerified("ada@example.com")

        val viaGoogle = client.google(idToken()).body<AuthResponse>()

        assertEquals(registered.user.id, viaGoogle.user.id)
        assertEquals(HttpStatusCode.OK, client.login("ada@example.com").status)
    }

    @Test
    fun `an unverified squatter account is taken over and its credentials revoked`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val squatter = client.register("ada@example.com")

        val owner = client.google(idToken()).body<AuthResponse>()

        assertEquals(squatter.user.id, owner.user.id)
        assertTrue(owner.user.emailVerified)
        assertEquals(HttpStatusCode.Unauthorized, client.login("ada@example.com").status)
        val refresh = client.post(ApiRoutes.Auth.REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(squatter.tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, refresh.status)
    }

    @Test
    fun `a Google account has no usable password until it sets one`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.google(idToken()).body<AuthResponse>()

        val overview = client.get(ApiRoutes.Security.BASE) { bearerAuth(ada.tokens.accessToken) }.body<SecurityOverview>()

        assertFalse(overview.passwordSet)
    }

    @Test
    fun `Google sign-in asks for the authenticator code when 2FA is on`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.google(idToken()).body<AuthResponse>()
        val setup = client.post(ApiRoutes.Security.TWO_FACTOR_SETUP) { bearerAuth(ada.tokens.accessToken) }
            .body<TwoFactorSetupResponse>()
        val secret = Base32.decode(setup.secret)
        val step = Totp.step(System.currentTimeMillis() / 1000)
        val enabled = client.post(ApiRoutes.Security.TWO_FACTOR_ENABLE) {
            bearerAuth(ada.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(TwoFactorEnableRequest(Totp.code(secret, step)))
        }
        assertEquals(HttpStatusCode.OK, enabled.status, enabled.bodyAsText())

        val second = client.google(idToken()).body<SignInResponse>()

        assertNull(second.tokens, "Google alone must not be enough once 2FA is on")
        // The next step's code: the one above is spent, and the window allows one step ahead.
        val verified = client.post(ApiRoutes.Auth.TWO_FACTOR_VERIFY) {
            contentType(ContentType.Application.Json)
            setBody(TwoFactorVerifyRequest(second.twoFactor!!.challengeToken, Totp.code(secret, step + 1)))
        }
        assertEquals(HttpStatusCode.OK, verified.status, verified.bodyAsText())
        assertEquals("ada@example.com", verified.body<AuthResponse>().user.email)
    }

    @Test
    fun `an unconfigured provider is unavailable`() = testApplication {
        useTestConfig(database, overrides = overrides)

        val response = jsonClient().post(ApiRoutes.Auth.oauth("apple")) {
            contentType(ContentType.Application.Json)
            setBody(OAuthSignInRequest(idToken = idToken()))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private suspend fun HttpClient.register(email: String) = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, "Password123", "Ada"))
    }.body<AuthResponse>()

    private suspend fun HttpClient.login(email: String) = post(ApiRoutes.Auth.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, "Password123"))
    }
}
