package com.elozino.splitfy.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/** `10.0.2.2` is how the Android emulator reaches the host machine's loopback. */
actual fun defaultApiBaseUrl(isDebug: Boolean): String =
    if (isDebug) "http://10.0.2.2:8080" else "https://splitfy.onrender.com"

actual fun createHttpClientEngine(): HttpClientEngine = OkHttp.create()
