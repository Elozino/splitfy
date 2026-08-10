package com.elozino.splitify.network

import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.api.dto.RefreshTokenRequest
import com.elozino.splitify.api.dto.TokenPair
import com.elozino.splitify.SessionManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the one [HttpClient] the whole app shares.
 *
 * Token refresh is handled by Ktor's Bearer provider: when a call comes back 401 the
 * plugin refreshes once and replays the original request. Screens therefore never
 * see a transient expiry, and a genuinely dead session signs the user out exactly
 * once through [SessionManager].
 */
class HttpClientFactory(
    private val engine: HttpClientEngine,
    private val config: ApiConfig,
    private val sessionManager: SessionManager,
) {

    fun create(): HttpClient = HttpClient(engine) {
        // Errors are mapped explicitly in `safeCall`, not thrown by the engine.
        expectSuccess = false

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }

        if (config.enableLogging) {
            install(Logging) {
                level = LogLevel.HEADERS
                logger = object : Logger {
                    override fun log(message: String) = println("[http] $message")
                }
                // Never let a bearer token reach the log.
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
        }

        install(HttpRequestRetry) {
            // Only idempotent-by-nature failures: 5xx and connection drops. A failed
            // POST is retried by the caller with its own idempotency key instead.
            retryOnServerErrors(maxRetries = 2)
            retryOnExceptionIf(maxRetries = 2) { _, cause -> cause !is kotlinx.coroutines.CancellationException }
            exponentialDelay()
        }

        install(Auth) {
            bearer {
                loadTokens {
                    sessionManager.currentTokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                }

                refreshTokens {
                    val refreshToken = oldTokens?.refreshToken
                        ?: sessionManager.currentTokens()?.refreshToken
                        ?: return@refreshTokens null

                    val response = client.post(ApiRoutes.Auth.REFRESH) {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshTokenRequest(refreshToken))
                    }

                    if (response.status != HttpStatusCode.OK) {
                        // The refresh token is revoked or expired: nothing to recover.
                        sessionManager.signOut()
                        return@refreshTokens null
                    }

                    val tokens = runCatching {
                        json.decodeFromString<TokenPair>(response.bodyAsText())
                    }.getOrElse {
                        sessionManager.signOut()
                        return@refreshTokens null
                    }

                    sessionManager.onTokensRefreshed(tokens)
                    BearerTokens(tokens.accessToken, tokens.refreshToken)
                }

                // The auth endpoints are the ones that mint tokens; sending a stale
                // bearer to them would trigger a pointless refresh round trip.
                sendWithoutRequest { request ->
                    ApiRoutes.Auth.BASE !in request.url.toString()
                }
            }
        }

        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }

    companion object {
        /**
         * Shared JSON configuration.
         *
         * `ignoreUnknownKeys` lets the backend add fields without shipping a new app;
         * without it, an additive server change would crash every installed client.
         */
        val json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
            isLenient = false
        }

        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }
}
