package com.elozino.splitify.core.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/** The iOS simulator shares the host's loopback, so plain localhost works. */
actual fun defaultApiBaseUrl(isDebug: Boolean): String =
    if (isDebug) "http://localhost:8080" else "https://api.splitfy.app"

actual fun createHttpClientEngine(): HttpClientEngine = Darwin.create()
