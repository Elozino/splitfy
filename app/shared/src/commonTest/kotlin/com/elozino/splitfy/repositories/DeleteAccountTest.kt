package com.elozino.splitfy.repositories

import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.SessionState
import com.elozino.splitfy.api.dto.TokenPair
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.network.ApiCaller
import com.elozino.splitfy.storage.SessionStorage
import com.elozino.splitfy.userMessage
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class DeleteAccountTest {

    private fun signedInRepository(status: HttpStatusCode, body: String): Pair<AuthRepositoryImpl, SessionManager> {
        val session = SessionManager(SessionStorage(MapSettings()))
        session.onAuthenticated(
            User(
                id = UserId("8b1d1d3c-0000-4000-8000-000000000001"),
                email = "ada@example.com",
                displayName = "Ada",
                defaultCurrency = CurrencyCode.NGN,
                createdAt = Instant.fromEpochSeconds(0),
            ),
            TokenPair(accessToken = "access", refreshToken = "refresh", expiresInSeconds = 900),
        )
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/v1/users/me", request.url.encodedPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("http://test.local") }
        }
        return AuthRepositoryImpl(client, ApiCaller(session), session) to session
    }

    @Test
    fun `a deleted account signs the device out`() = runTest {
        val (repository, session) = signedInRepository(HttpStatusCode.NoContent, "")

        assertIs<DataResult.Success<Unit>>(repository.deleteAccount())
        assertIs<SessionState.SignedOut>(session.state.value)
    }

    @Test
    fun `a refusal keeps the session and passes the server's reason through`() = runTest {
        val body = """{"code":"CONFLICT","message":"Settle up before deleting your account. You still have a balance in: Flat (NGN -50.00)"}"""
        val (repository, session) = signedInRepository(HttpStatusCode.Conflict, body)

        val failure = assertIs<DataResult.Failure>(repository.deleteAccount())
        assertEquals("Settle up before deleting your account. You still have a balance in: Flat (NGN -50.00)", failure.error.userMessage())
        assertIs<SessionState.SignedIn>(session.state.value)
    }
}
