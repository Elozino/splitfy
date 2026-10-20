package com.elozino.splitfy.backend

import com.elozino.splitfy.backend.storage.ObjectStorage
import com.elozino.splitfy.backend.storage.SigV4Presigner
import com.elozino.splitfy.backend.utils.AppConfig
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SigV4PresignerTest {

    /**
     * AWS's published example for a presigned GET, from "Authenticating Requests:
     * Using Query Parameters (AWS Signature Version 4)" in the Amazon S3 API reference.
     */
    @Test
    fun `matches the AWS presigned URL test vector`() {
        val presigner = SigV4Presigner(
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
        )

        val url = presigner.presign(
            method = "GET",
            scheme = "https",
            host = "examplebucket.s3.amazonaws.com",
            path = "/test.txt",
            expires = 24.hours,
            now = Instant.parse("2013-05-24T00:00:00Z"),
        )

        assertEquals(
            "https://examplebucket.s3.amazonaws.com/test.txt" +
                "?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request" +
                "&X-Amz-Date=20130524T000000Z" +
                "&X-Amz-Expires=86400" +
                "&X-Amz-SignedHeaders=host" +
                "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404",
            url,
        )
    }

    @Test
    fun `encodes everything but unreserved characters`() {
        assertEquals("a-b_c.d~e%20f%2Bg%2F", SigV4Presigner.encode("a-b_c.d~e f+g/"))
        assertEquals("/bucket/k%C3%A9y/x.jpg", SigV4Presigner.encode("/bucket/kéy/x.jpg", keepSlash = true))
    }

    @Test
    fun `upload URLs sign the declared type and size`() {
        val storage = storage()

        val signed = storage.presignUpload("groups/g/receipts/abc.jpg", "image/jpeg", 1234)

        assertTrue(signed.url.startsWith("https://acct.r2.cloudflarestorage.com/splitfy/groups/g/receipts/abc.jpg?"))
        assertTrue("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost" in signed.url, signed.url)
        assertTrue("%2Fauto%2Fs3%2Faws4_request" in signed.url)
        assertEquals(mapOf("Content-Type" to "image/jpeg"), signed.headers)
        assertEquals(NOW + 15.minutes, signed.expiresAt)
    }

    @Test
    fun `cover links are stable within the hour, or public when a host is configured`() {
        val early = storage(now = Instant.parse("2026-01-01T10:05:00Z")).displayUrl("groups/g/covers/a.jpg")
        val late = storage(now = Instant.parse("2026-01-01T10:55:00Z")).displayUrl("groups/g/covers/a.jpg")
        assertEquals(early, late)
        assertTrue("X-Amz-Date=20260101T100000Z" in early!!)

        val public = storage(publicBaseUrl = "https://cdn.splitfy.app").displayUrl("groups/g/covers/a.jpg")
        assertEquals("https://cdn.splitfy.app/groups/g/covers/a.jpg", public)
    }

    private fun storage(now: Instant = NOW, publicBaseUrl: String? = null) = ObjectStorage(
        AppConfig.StorageConfig(
            endpoint = "https://acct.r2.cloudflarestorage.com",
            bucket = "splitfy",
            accessKey = "key",
            secretKey = "secret",
            publicBaseUrl = publicBaseUrl,
        ),
        clock = object : Clock { override fun now() = now },
    )

    private companion object {
        val NOW = Instant.parse("2026-01-01T12:00:00Z")
    }
}
