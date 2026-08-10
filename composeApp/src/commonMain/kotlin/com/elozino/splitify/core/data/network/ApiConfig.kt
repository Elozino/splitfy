package com.elozino.splitify.core.data.network

import io.ktor.client.engine.HttpClientEngine

/**
 * Where the API lives and how chatty the client is.
 *
 * Built by the DI graph from the flag each platform entry point passes in, so a
 * debug build can point at a local server while release builds always talk to
 * production.
 */
data class ApiConfig(
    val baseUrl: String,
    val enableLogging: Boolean,
) {
    companion object {
        fun forBuild(isDebug: Boolean) = ApiConfig(
            baseUrl = defaultApiBaseUrl(isDebug),
            // Request logging would leak user data in a shipped build.
            enableLogging = isDebug,
        )
    }
}

/**
 * `localhost` means different things per platform: the Android emulator reaches the
 * host machine at `10.0.2.2`, while the iOS simulator shares the host's loopback.
 */
expect fun defaultApiBaseUrl(isDebug: Boolean): String

/** OkHttp on Android, Darwin (NSURLSession) on iOS. */
expect fun createHttpClientEngine(): HttpClientEngine
