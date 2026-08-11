package com.elozino.splitfy.backend.plugins

import com.elozino.splitfy.backend.utils.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import kotlinx.serialization.json.Json

/**
 * Transport-level concerns: JSON, CORS, compression and proxy awareness.
 */
fun Application.configureHttp(config: AppConfig) {

    install(ContentNegotiation) {
        json(
            Json {
                // Additive API changes must not break older clients.
                ignoreUnknownKeys = true
                // Absent optional fields fall back to their Kotlin defaults.
                explicitNulls = false
                encodeDefaults = true
                prettyPrint = !config.environment.isProduction
            }
        )
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        if (config.environment.isProduction) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }

    install(Compression) {
        // Below this size the CPU cost outweighs the bytes saved.
        gzip { minimumSize(MIN_COMPRESSION_BYTES) }
    }

    // Only trust forwarding headers when a known proxy terminates TLS in front of us;
    // otherwise a client could spoof its own IP and defeat rate limiting.
    if (config.environment != AppConfig.Environment.LOCAL) {
        install(XForwardedHeaders)
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.XRequestId)
        allowCredentials = true

        if (config.cors.allowedHosts.isEmpty() && !config.environment.isProduction) {
            // Native apps do not send Origin; this only matters for local web tooling.
            anyHost()
        } else {
            config.cors.allowedHosts.forEach { host ->
                allowHost(host, schemes = listOf("https"))
            }
        }
    }
}

private const val MIN_COMPRESSION_BYTES = 1024L
