package com.elozino.splitfy.network

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.SessionState
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.TokenPair
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.storage.SessionStorage
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * The access token has expired (every API call answers 401), so the client must
 * refresh. Whether that ends the session depends on why the refresh failed.
 */
class TokenRefreshTest {

    private fun signedInSession(): SessionManager = SessionManager(SessionStorage(MapSettings())).apply {
        onAuthenticated(
            User(UserId("u1"), "ada@example.com", "Ada", createdAt = Instant.fromEpochSeconds(0)),
            TokenPair("expired-access", "refresh-1", 900),
        )
    }

    private fun client(session: SessionManager, refreshStatus: HttpStatusCode) = HttpClientFactory(
        engine = MockEngine { request ->
            if (request.url.encodedPath == ApiRoutes.Auth.REFRESH) {
                respond("""{"code":"INTERNAL_ERROR","message":"down"}""", refreshStatus, jsonHeaders)
            } else {
                respond("""{"code":"UNAUTHORIZED","message":"expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
            }
        },
        config = ApiConfig(baseUrl = "http://test.local", enableLogging = false),
        sessionManager = session,
    ).create()

    /**
     * The client's timeout and retry delays need a real clock: under runTest's
     * virtual time the 30s request timeout "expires" before the mock answers.
     */
    private suspend fun <T> realTime(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `a server error during refresh fails the call but keeps the session`() = runTest {
        val session = signedInSession()
        val caller = ApiCaller(session)
        val http = client(session, HttpStatusCode.ServiceUnavailable)

        val result = realTime { caller.execute<Unit> { http.get(ApiRoutes.Groups.BASE) } }

        val failure = assertIs<DataResult.Failure>(result)
        assertIs<DataError.Api>(failure.error)
        assertIs<SessionState.SignedIn>(session.state.value)
        assertEquals("refresh-1", session.currentTokens()?.refreshToken)
    }

    @Test
    fun `a rejected refresh token ends the session`() = runTest {
        val session = signedInSession()
        val caller = ApiCaller(session)
        val http = client(session, HttpStatusCode.Unauthorized)

        val result = realTime { caller.execute<Unit> { http.get(ApiRoutes.Groups.BASE) } }

        assertEquals(DataError.Unauthenticated, assertIs<DataResult.Failure>(result).error)
        assertIs<SessionState.SignedOut>(session.state.value)
    }
}

