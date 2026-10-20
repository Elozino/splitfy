package com.elozino.splitfy.uploads

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.network.ApiCaller
import com.elozino.splitfy.storage.SessionStorage
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class UploadRepositoryTest {

    private val slotJson = """
        {
          "uploadUrl": "https://storage.example/splitfy/groups/g/receipts/k.jpg?X-Amz-Signature=abc",
          "objectKey": "groups/g/receipts/k.jpg",
          "expiresAt": "2026-01-01T00:15:00Z",
          "headers": { "Content-Type": "image/jpeg" }
        }
    """.trimIndent()

    private val apiRequests = mutableListOf<HttpRequestData>()
    private val storageRequests = mutableListOf<Pair<HttpRequestData, ByteArray>>()

    private fun repository(apiStatus: HttpStatusCode = HttpStatusCode.Created, storageStatus: HttpStatusCode = HttpStatusCode.OK): UploadRepositoryImpl {
        val api = HttpClient(MockEngine { request ->
            apiRequests += request
            respond(slotJson, apiStatus, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest {
                url("http://api.test")
                contentType(ContentType.Application.Json)
            }
        }
        val storage = HttpClient(MockEngine { request ->
            storageRequests += request to request.body.toByteArray()
            respond("", storageStatus)
        }) { expectSuccess = false }
        return UploadRepositoryImpl(api, ApiCaller(SessionManager(SessionStorage(MapSettings()))), storage)
    }

    @Test
    fun `asks the API for a slot and then puts the bytes straight to storage`() = runTest {
        val bytes = ByteArray(1234) { it.toByte() }

        val result = repository().upload("g", UploadPurpose.RECEIPT, UploadFile(bytes, UploadRules.JPEG))

        assertEquals(DataResult.Success("groups/g/receipts/k.jpg"), result)
        assertTrue(apiRequests.single().url.encodedPath.endsWith("/groups/g/uploads"))

        val (put, body) = storageRequests.single()
        assertEquals(HttpMethod.Put, put.method)
        assertEquals("https://storage.example/splitfy/groups/g/receipts/k.jpg?X-Amz-Signature=abc", put.url.toString())
        assertEquals(ContentType.Image.JPEG, put.body.contentType)
        assertEquals(1234L, put.body.contentLength)
        assertContentEquals(bytes, body)
        // The presigned URL is the credential; a bearer token would make storage refuse it.
        assertNull(put.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a storage rejection is a failure and never a sign-out`() = runTest {
        val result = repository(storageStatus = HttpStatusCode.Forbidden)
            .upload("g", UploadPurpose.RECEIPT, UploadFile(byteArrayOf(1), UploadRules.JPEG))

        assertIs<DataError.Api>((result as DataResult.Failure).error)
    }

    @Test
    fun `files over the limit never leave the device`() = runTest {
        val repository = repository()
        val result = repository.upload(
            "g",
            UploadPurpose.RECEIPT,
            UploadFile(ByteArray((UploadRules.MAX_BYTES + 1).toInt()), UploadRules.JPEG),
        )

        assertIs<DataResult.Failure>(result)
        assertTrue(apiRequests.isEmpty())
        assertTrue(storageRequests.isEmpty())
    }
}
