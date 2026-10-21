package com.elozino.splitfy.backend

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.ForgotPasswordRequest
import com.elozino.splitfy.api.dto.LinkedAccountsResponse
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.ResetPasswordRequest
import com.elozino.splitfy.api.dto.VerifyOtpRequest
import com.elozino.splitfy.api.dto.VerifyOtpResponse
import com.elozino.splitfy.backend.services.IdentityTokenVerifier
import com.elozino.splitfy.backend.services.OtpNotifier
import com.elozino.splitfy.backend.services.SocialAuthService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.dsl.module

class LinkedAccountsTest {

    private val database = TestDatabase("splitfy_linked")

    private val clientId = "test-client"
    private val keyId = "test-key"
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey

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

    private val issuers = mapOf(OAuthProvider.GOOGLE to "https://accounts.google.com", OAuthProvider.APPLE to "https://appleid.apple.com")

    private class ResetCodes : OtpNotifier {
        val codes = ConcurrentHashMap<String, String>()
        override suspend fun sendPasswordResetCode(email: String, code: String) {
            codes[email] = code
        }
        override suspend fun sendEmailVerificationCode(email: String, code: String) = Unit
        override suspend fun sendEmailChangeCode(email: String, code: String) = Unit
        override suspend fun sendEmailChangedNotice(oldEmail: String, newEmail: String) = Unit
    }

    private val notifier = ResetCodes()

    private val overrides = listOf(
        module {
            single<OtpNotifier> { notifier }
            single {
                SocialAuthService(
                    get(), get(), get(), get(), get(), get(),
                    verifiers = issuers.map { (provider, issuer) ->
                        IdentityTokenVerifier(provider, keys, setOf(issuer), setOf(clientId))
                    },
                )
            }
        }
    )

    @BeforeTest
    fun setUp() = database.reset()

    private fun idToken(provider: OAuthProvider, subject: String, email: String = "ada@example.com"): String = JWT.create()
        .withKeyId(keyId)
        .withIssuer(issuers.getValue(provider))
        .withAudience(clientId)
        .withSubject(subject)
        .withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .withClaim("email", email)
        .withClaim("email_verified", true)
        .sign(Algorithm.RSA256(publicKey, privateKey))

    @Test
    fun `a password account links Google, sees it, and can unlink it`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")

        val before = client.linked(ada.token)
        assertTrue(before.hasPassword)
        assertTrue(before.identities.isEmpty())
        assertEquals(listOf(OAuthProvider.GOOGLE, OAuthProvider.APPLE), before.availableProviders)

        // The Google address need not match the account's.
        val linked = client.link(ada.token, OAuthProvider.GOOGLE, idToken(OAuthProvider.GOOGLE, "g-ada", "ada.personal@gmail.com"))
        assertEquals(HttpStatusCode.OK, linked.status)
        val identity = linked.body<LinkedAccountsResponse>().identities.single()
        assertEquals(OAuthProvider.GOOGLE, identity.provider)
        assertEquals("ada.personal@gmail.com", identity.email)

        // Signing in with that Google account now reaches Ada's account.
        val signIn = client.post(ApiRoutes.Auth.oauth("google")) {
            contentType(ContentType.Application.Json)
            setBody(OAuthSignInRequest(idToken(OAuthProvider.GOOGLE, "g-ada", "ada.personal@gmail.com")))
        }.body<AuthResponse>()
        assertEquals(ada.id, signIn.user.id.value)

        val unlinked = client.unlink(ada.token, OAuthProvider.GOOGLE)
        assertEquals(HttpStatusCode.OK, unlinked.status)
        assertTrue(unlinked.body<LinkedAccountsResponse>().identities.isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.unlink(ada.token, OAuthProvider.GOOGLE).status)
    }

    @Test
    fun `an identity that belongs to another account cannot be linked`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")
        val bob = client.register("bob@example.com")

        client.link(ada.token, OAuthProvider.GOOGLE, idToken(OAuthProvider.GOOGLE, "g-shared")).also {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        // Linking it again to the same account is a no-op...
        assertEquals(HttpStatusCode.OK, client.link(ada.token, OAuthProvider.GOOGLE, idToken(OAuthProvider.GOOGLE, "g-shared")).status)
        // ...to another account it is refused...
        assertEquals(HttpStatusCode.Conflict, client.link(bob.token, OAuthProvider.GOOGLE, idToken(OAuthProvider.GOOGLE, "g-shared")).status)
        // ...and a second Google account on the same splitfy account is refused too.
        assertEquals(HttpStatusCode.Conflict, client.link(ada.token, OAuthProvider.GOOGLE, idToken(OAuthProvider.GOOGLE, "g-other")).status)

        assertTrue(client.linked(bob.token).identities.isEmpty())
    }

    @Test
    fun `a token that does not verify is rejected without signing the caller out`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val ada = client.register("ada@example.com")

        val response = client.link(ada.token, OAuthProvider.GOOGLE, "not-a-jwt")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `the last way into a Google-only account cannot be unlinked until a password is set`() = testApplication {
        useTestConfig(database, overrides = overrides)
        val client = jsonClient()
        val signIn = client.post(ApiRoutes.Auth.oauth("google")) {
            contentType(ContentType.Application.Json)
            setBody(OAuthSignInRequest(idToken(OAuthProvider.GOOGLE, "g-ada")))
        }.body<AuthResponse>()
        val token = signIn.tokens.accessToken

        assertFalse(client.linked(token).hasPassword)
        assertEquals(HttpStatusCode.Conflict, client.unlink(token, OAuthProvider.GOOGLE).status)

        // With Apple linked as well, Google can go.
        client.link(token, OAuthProvider.APPLE, idToken(OAuthProvider.APPLE, "a-ada"))
        assertEquals(HttpStatusCode.OK, client.unlink(token, OAuthProvider.GOOGLE).status)
        assertEquals(HttpStatusCode.Conflict, client.unlink(token, OAuthProvider.APPLE).status)

        // Setting a password through reset makes the last identity removable.
        client.resetPassword("ada@example.com")
        val fresh = client.post(ApiRoutes.Auth.oauth("apple")) {
            contentType(ContentType.Application.Json)
            setBody(OAuthSignInRequest(idToken(OAuthProvider.APPLE, "a-ada")))
        }.body<AuthResponse>().tokens.accessToken
        assertTrue(client.linked(fresh).hasPassword)
        assertEquals(HttpStatusCode.OK, client.unlink(fresh, OAuthProvider.APPLE).status)
    }

    private data class Account(val id: String, val token: String)

    private suspend fun HttpClient.register(email: String): Account {
        val auth = post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "Password123", email.substringBefore('@')))
        }.body<AuthResponse>()
        return Account(auth.user.id.value, auth.tokens.accessToken)
    }

    private suspend fun HttpClient.linked(token: String): LinkedAccountsResponse =
        get(ApiRoutes.Users.IDENTITIES) { bearerAuth(token) }.body()

    private suspend fun HttpClient.link(token: String, provider: OAuthProvider, idToken: String): HttpResponse =
        post(ApiRoutes.Users.identity(provider.path)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(OAuthSignInRequest(idToken))
        }

    private suspend fun HttpClient.unlink(token: String, provider: OAuthProvider): HttpResponse =
        delete(ApiRoutes.Users.identity(provider.path)) { bearerAuth(token) }

    private suspend fun HttpClient.resetPassword(email: String) {
        post(ApiRoutes.Auth.FORGOT_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(email))
        }
        val resetToken = post(ApiRoutes.Auth.VERIFY_OTP) {
            contentType(ContentType.Application.Json)
            setBody(VerifyOtpRequest(email, notifier.codes.getValue(email)))
        }.body<VerifyOtpResponse>().resetToken
        post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(email, resetToken, "NewPassword123"))
        }
    }
}
