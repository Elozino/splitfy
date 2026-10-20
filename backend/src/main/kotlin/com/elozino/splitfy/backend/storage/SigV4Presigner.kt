package com.elozino.splitfy.backend.storage

import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * AWS Signature Version 4, query-string ("presigned URL") form.
 *
 * Written by hand rather than pulling in the AWS SDK: presigning is a pure function
 * of the request and a key, about a hundred lines, and needs no network call. The SDK
 * would add its own HTTP stack and several megabytes of jars to sign a URL. The
 * implementation is pinned to AWS's published test vector in `SigV4PresignerTest`.
 *
 * Every header in [presign]'s `headers` is signed, so the storage service rejects a
 * request that sends a different value — this is how an upload's declared size and
 * type are enforced without the bytes ever passing through this server.
 */
class SigV4Presigner(
    private val accessKey: String,
    private val secretKey: String,
    private val region: String,
    private val service: String = "s3",
) {

    /**
     * Returns a URL that performs [method] on [path] at [host] until [expires] after
     * [now]. [path] is the raw (unencoded) object path, starting with `/`.
     */
    fun presign(
        method: String,
        scheme: String,
        host: String,
        path: String,
        expires: Duration,
        now: Instant,
        headers: Map<String, String> = emptyMap(),
    ): String {
        require(expires.inWholeSeconds in 1..MAX_EXPIRY_SECONDS) { "SigV4 links last 1 s to 7 days" }

        val instant = now.toJavaInstant().atOffset(ZoneOffset.UTC)
        val amzDate = AMZ_DATE.format(instant)
        val date = amzDate.substring(0, 8)
        val scope = "$date/$region/$service/aws4_request"

        val signedHeaders = (headers.mapKeys { it.key.lowercase() } + ("host" to host)).toSortedMap()
        val signedHeaderNames = signedHeaders.keys.joinToString(";")

        val query = sortedMapOf(
            "X-Amz-Algorithm" to ALGORITHM,
            "X-Amz-Credential" to "$accessKey/$scope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to expires.inWholeSeconds.toString(),
            "X-Amz-SignedHeaders" to signedHeaderNames,
        )
        val canonicalQuery = query.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val canonicalPath = encode(path, keepSlash = true)

        val canonicalRequest = listOf(
            method,
            canonicalPath,
            canonicalQuery,
            signedHeaders.entries.joinToString("") { (name, value) -> "$name:${value.trim()}\n" },
            signedHeaderNames,
            UNSIGNED_PAYLOAD,
        ).joinToString("\n")

        val stringToSign = listOf(ALGORITHM, amzDate, scope, sha256Hex(canonicalRequest)).joinToString("\n")

        val signingKey = listOf(date, region, service, "aws4_request")
            .fold("AWS4$secretKey".toByteArray()) { key, part -> hmac(key, part) }
        val signature = hmac(signingKey, stringToSign).toHex()

        return "$scheme://$host$canonicalPath?$canonicalQuery&X-Amz-Signature=$signature"
    }

    companion object {
        const val ALGORITHM = "AWS4-HMAC-SHA256"
        const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
        const val MAX_EXPIRY_SECONDS = 7L * 24 * 60 * 60

        private val AMZ_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        /** RFC 3986 percent-encoding as SigV4 defines it: only unreserved characters pass through. */
        internal fun encode(value: String, keepSlash: Boolean = false): String = buildString {
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val char = byte.toInt().toChar()
                when {
                    char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-_.~" -> append(char)
                    char == '/' && keepSlash -> append(char)
                    else -> append('%').append("%02X".format(byte.toInt() and 0xFF))
                }
            }
        }

        private fun hmac(key: ByteArray, data: String): ByteArray =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(data.toByteArray(Charsets.UTF_8))
            }

        private fun sha256Hex(data: String): String =
            MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8)).toHex()

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
