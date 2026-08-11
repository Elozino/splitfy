package com.elozino.splitify.network

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
        /**
         * [baseUrl] is what the platform's build configuration resolved — Android's
         * `config.properties` by way of `BuildConfig`, iOS's `.xcconfig` by way of
         * Info.plist. Blank or absent, the platform default below applies, which is
         * what happens in tests and in any build predating those files.
         */
        fun forBuild(isDebug: Boolean, baseUrl: String? = null) = ApiConfig(
            baseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: defaultApiBaseUrl(isDebug),
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
