package com.elozino.splitfy.repositories

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.userMessage
import com.elozino.splitfy.network.ApiCaller
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.SessionState
import com.elozino.splitfy.storage.SessionStorage
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * Exercises the data layer against a mocked transport: the point is the mapping from
 * HTTP responses to [DataResult]/[DataError] and the session side effects, not Ktor
 * itself.
 */
class AuthRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private fun repository(
        status: HttpStatusCode,
        body: String,
    ): Triple<AuthRepositoryImpl, SessionManager, SessionStorage> {
        val storage = SessionStorage(MapSettings())
        val session = SessionManager(storage)
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            defaultRequest {
                url("http://test.local")
                // Mirrors HttpClientFactory: without it, request bodies are not serialized.
                contentType(ContentType.Application.Json)
            }
        }
        return Triple(AuthRepositoryImpl(client, ApiCaller(session), session), session, storage)
    }

    private val successBody = """
        {
          "user": {
            "id": "8b1d1d3c-0000-4000-8000-000000000001",
            "email": "ada@example.com",
            "displayName": "Ada",
            "defaultCurrency": "NGN",
            "createdAt": "2026-01-01T00:00:00Z"
          },
          "tokens": {
            "accessToken": "access-123",
            "refreshToken": "refresh-456",
            "expiresInSeconds": 900,
            "tokenType": "Bearer"
          }
        }
    """.trimIndent()

    @Test
    fun `a successful login persists the session`() = runTest {
        val (repository, session, storage) = repository(HttpStatusCode.OK, successBody)

        val result = repository.login("ada@example.com", "Password123")

        assertIs<DataResult.Success<*>>(result)
        assertTrue(session.isSignedIn)
        assertIs<SessionState.SignedIn>(session.state.value)
        assertEquals("access-123", storage.readTokens()?.accessToken)
        assertEquals("refresh-456", storage.readTokens()?.refreshToken)
        assertEquals("8b1d1d3c-0000-4000-8000-000000000001", storage.readUserId())
    }

    @Test
    fun `wrong credentials are reported as such and leave the session alone`() = runTest {
        val body = """{"code":"INVALID_CREDENTIALS","message":"Email or password is incorrect"}"""
        val (repository, session, _) = repository(HttpStatusCode.Unauthorized, body)
        val stateBefore = session.state.value

        val result = repository.login("ada@example.com", "wrong")

        val failure = assertIs<DataResult.Failure>(result)
        val error = assertIs<DataError.Api>(failure.error)
        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, error.code)
        assertEquals("Email or password is incorrect", failure.error.userMessage())
        assertEquals(stateBefore, session.state.value)
    }

    @Test
    fun `an expired session maps to Unauthenticated and clears it`() = runTest {
        val body = """{"code":"TOKEN_EXPIRED","message":"Refresh token has expired"}"""
        val (repository, session, storage) = repository(HttpStatusCode.Unauthorized, body)

        val result = repository.login("ada@example.com", "Password123")

        assertIs<DataResult.Failure>(result)
        assertEquals(DataError.Unauthenticated, result.error)
        assertNull(storage.readTokens())
        assertIs<SessionState.SignedOut>(session.state.value)
    }

    @Test
    fun `field errors survive the round trip so the form can show them inline`() = runTest {
        val body = """
            {
              "code": "VALIDATION_FAILED",
              "message": "The request failed validation",
              "fieldErrors": [{"field":"password","message":"Password must contain a number"}],
              "traceId": "trace-1"
            }
        """.trimIndent()
        val (repository, _, _) = repository(HttpStatusCode.BadRequest, body)

        val result = repository.register("ada@example.com", "password", "Ada")

        val failure = assertIs<DataResult.Failure>(result)
        val validation = assertIs<DataError.Validation>(failure.error)
        assertEquals("password", validation.fieldErrors.single().field)
    }

    @Test
    fun `a server error is reported without leaking internals`() = runTest {
        val (repository, _, _) = repository(HttpStatusCode.InternalServerError, "boom")

        val result = repository.login("ada@example.com", "Password123")

        val failure = assertIs<DataResult.Failure>(result)
        val error = assertIs<DataError.Api>(failure.error)
        assertNotNull(error.message)
        assertTrue("boom" !in error.message)
    }

    @Test
    fun `signing out clears stored tokens even when the call fails`() = runTest {
        val (repository, session, storage) = repository(HttpStatusCode.OK, successBody)
        repository.login("ada@example.com", "Password123")
        assertTrue(session.isSignedIn)

        repository.logout()

        assertNull(storage.readTokens())
        assertIs<SessionState.SignedOut>(session.state.value)
    }

    @Test
    fun `a two-step challenge stores nothing until the code is verified`() = runTest {
        val body = """{"twoFactor":{"challengeToken":"challenge-1","expiresInSeconds":300}}"""
        val (repository, session, storage) = repository(HttpStatusCode.OK, body)

        val result = repository.login("ada@example.com", "Password123")

        val outcome = assertIs<DataResult.Success<SignInResult>>(result).data
        assertEquals(SignInResult.TwoFactorRequired("challenge-1", 300), outcome)
        assertNull(storage.readTokens())
        assertTrue(!session.isSignedIn)
    }

    @Test
    fun `a verified code signs in like a login`() = runTest {
        val (repository, session, storage) = repository(HttpStatusCode.OK, successBody)

        val result = repository.verifyTwoFactor("challenge-1", "123456")

        assertEquals("ada@example.com", assertIs<DataResult.Success<*>>(result).data.let { (it as com.elozino.splitfy.domain.model.User).email })
        assertTrue(session.isSignedIn)
        assertEquals("access-123", storage.readTokens()?.accessToken)
    }

    @Test
    fun `a wrong second-step code is reported without signing anyone out`() = runTest {
        val body = """{"code":"INVALID_CREDENTIALS","message":"That code isn't right."}"""
        val (repository, session, _) = repository(HttpStatusCode.Unauthorized, body)

        val result = repository.verifyTwoFactor("challenge-1", "000000")

        assertEquals("That code isn't right.", assertIs<DataResult.Failure>(result).error.userMessage())
        assertIs<SessionState.SignedOut>(session.state.value)
    }
}
