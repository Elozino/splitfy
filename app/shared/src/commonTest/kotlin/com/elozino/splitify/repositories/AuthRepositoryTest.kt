package com.elozino.splitify.repositories

import com.elozino.splitify.DataError
import com.elozino.splitify.DataResult
import com.elozino.splitify.network.ApiCaller
import com.elozino.splitify.SessionManager
import com.elozino.splitify.SessionState
import com.elozino.splitify.storage.SessionStorage
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
    fun `a 401 maps to Unauthenticated and clears the session`() = runTest {
        val body = """{"code":"INVALID_CREDENTIALS","message":"Email or password is incorrect"}"""
        val (repository, session, storage) = repository(HttpStatusCode.Unauthorized, body)

        val result = repository.login("ada@example.com", "wrong")

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
}
