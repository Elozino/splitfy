package com.elozino.splitfy.backend.storage

import com.elozino.splitfy.backend.utils.AppConfig
import com.elozino.splitfy.backend.utils.AppException
import java.net.URI
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A signed request the client performs itself. */
data class PresignedRequest(
    val url: String,
    val expiresAt: Instant,
    /** Headers the client must send exactly as given; they are part of the signature. */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * The bucket, as far as this server is concerned: it never moves file bytes, it only
 * signs URLs for the app to use directly. Addresses objects path-style
 * (`endpoint/bucket/key`), which both AWS S3 and Cloudflare R2 accept.
 *
 * With storage unconfigured every operation fails with 503 and the app hides its
 * upload controls (see `ApiRoutes.Uploads.CONFIG`).
 */
class ObjectStorage(
    private val config: AppConfig.StorageConfig,
    private val clock: Clock = Clock.System,
) {
    val isEnabled: Boolean get() = config.isConfigured

    private val endpoint: URI? = config.endpoint.takeIf { isEnabled }?.let(URI::create)
    private val presigner = SigV4Presigner(config.accessKey, config.secretKey, config.region)

    fun presignUpload(key: String, contentType: String, sizeBytes: Long, ttl: Duration = UPLOAD_TTL): PresignedRequest {
        val now = clock.now()
        val headers = mapOf("Content-Type" to contentType, "Content-Length" to sizeBytes.toString())
        return PresignedRequest(
            url = sign("PUT", key, ttl, now, headers),
            expiresAt = now + ttl,
            // Content-Length is set by every HTTP client from the body itself, and most
            // refuse to let callers set it; only Content-Type needs echoing back.
            headers = mapOf("Content-Type" to contentType),
        )
    }

    /** A short-lived read link for a private object, such as a receipt. */
    fun presignDownload(key: String, ttl: Duration = DOWNLOAD_TTL): PresignedRequest {
        val now = clock.now()
        return PresignedRequest(url = sign("GET", key, ttl, now), expiresAt = now + ttl)
    }

    /**
     * A URL to display a cover photo with. Uses the public host when one is
     * configured. Otherwise it is a signed link whose signing time is rounded down to
     * the hour, so every response within that hour carries the same URL and image
     * caches keep working; it stays valid for at least an hour after it is issued.
     */
    fun displayUrl(key: String): String? {
        if (!isEnabled) return null
        config.publicBaseUrl?.let { return "$it/${SigV4Presigner.encode(key, keepSlash = true)}" }
        val now = clock.now()
        val hourStart = Instant.fromEpochSeconds(now.epochSeconds - now.epochSeconds % 3600)
        return sign("GET", key, DISPLAY_TTL, hourStart)
    }

    private fun sign(method: String, key: String, ttl: Duration, now: Instant, headers: Map<String, String> = emptyMap()): String {
        val endpoint = endpoint ?: throw unavailable()
        val host = if (endpoint.port == -1) endpoint.host else "${endpoint.host}:${endpoint.port}"
        val basePath = endpoint.rawPath.orEmpty().trimEnd('/')
        return presigner.presign(
            method = method,
            scheme = endpoint.scheme,
            host = host,
            path = "$basePath/${config.bucket}/$key",
            expires = ttl,
            now = now,
            headers = headers,
        )
    }

    companion object {
        val UPLOAD_TTL = 15.minutes
        val DOWNLOAD_TTL = 5.minutes
        val DISPLAY_TTL = 2.hours

        fun unavailable() = AppException.Unavailable("File uploads are not available on this server")
    }
}
