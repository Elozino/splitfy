package com.elozino.splitfy.network

import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.HttpClientEngine
import platform.Foundation.NSBundle

/**
 * `ApiBaseUrl` is written into Info.plist from API_SCHEME/API_HOST in the active
 * .xcconfig, so switching environments is an Xcode scheme change rather than a code
 * change. The literals below are the fallback for contexts with no app bundle to
 * read — unit tests, most notably. The iOS simulator shares the host's loopback, so
 * plain localhost reaches a locally running backend.
 */
actual fun defaultApiBaseUrl(isDebug: Boolean): String =
    configuredApiBaseUrl()
        ?: if (isDebug) "http://localhost:8080" else "https://api.splitfy.app"

private fun configuredApiBaseUrl(): String? =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("ApiBaseUrl") as? String)
        // An unexpanded `$(API_HOST)` means the build setting was missing; treat
        // that as unconfigured rather than letting it reach the HTTP client.
        ?.takeIf { it.isNotBlank() && !it.contains("$(") }

actual fun createHttpClientEngine(): HttpClientEngine = Darwin.create()
