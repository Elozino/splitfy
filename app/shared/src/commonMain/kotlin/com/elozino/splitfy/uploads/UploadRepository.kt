package com.elozino.splitfy.uploads

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.FileLink
import com.elozino.splitfy.api.dto.UploadConfig
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.api.dto.UploadSlot
import com.elozino.splitfy.api.dto.UploadSlotRequest
import com.elozino.splitfy.network.ApiCaller
import com.elozino.splitfy.network.createHttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/** A file to upload: already compressed, with its MIME type. */
class UploadFile(val bytes: ByteArray, val contentType: String)

interface UploadRepository {
    /** Whether this server accepts uploads at all; the UI hides its controls when it doesn't. */
    suspend fun config(): DataResult<UploadConfig>

    /** Uploads [file] for [groupId] and returns the object key to attach it with. */
    suspend fun upload(groupId: String, purpose: UploadPurpose, file: UploadFile): DataResult<String>

    /** A short-lived link to an expense's receipt. */
    suspend fun receiptLink(groupId: String, expenseId: String): DataResult<FileLink>
}

/**
 * Two-step upload: ask the API for a presigned slot, then PUT the bytes straight to
 * object storage.
 *
 * [storageClient] must be a plain client. The API client adds a bearer token and a
 * base URL to every request; storage would reject the first (a presigned URL is
 * already the credential) and the second would send the file to the API instead.
 */
class UploadRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
    private val storageClient: HttpClient,
) : UploadRepository {

    override suspend fun config(): DataResult<UploadConfig> =
        caller.execute { client.get(ApiRoutes.Uploads.CONFIG) }

    override suspend fun upload(groupId: String, purpose: UploadPurpose, file: UploadFile): DataResult<String> {
        if (file.bytes.size > UploadRules.MAX_BYTES) {
            return DataResult.Failure(DataError.Api(ApiErrorCode.VALIDATION_FAILED, "That file is larger than 10 MB"))
        }
        val slot = caller.execute<UploadSlot> {
            client.post(ApiRoutes.Groups.uploads(groupId)) {
                setBody(UploadSlotRequest(purpose, file.contentType, file.bytes.size.toLong()))
            }
        }
        return when (slot) {
            is DataResult.Failure -> slot
            is DataResult.Success -> put(slot.data, file)
        }
    }

    override suspend fun receiptLink(groupId: String, expenseId: String): DataResult<FileLink> =
        caller.execute { client.get(ApiRoutes.Expenses.receipt(groupId, expenseId)) }

    /**
     * Storage speaks XML errors and its 401/403 mean "bad signature", not "signed
     * out", so none of [ApiCaller]'s mapping applies here.
     */
    private suspend fun put(slot: UploadSlot, file: UploadFile): DataResult<String> = try {
        val response = storageClient.put(slot.uploadUrl) {
            slot.headers.forEach { (name, value) ->
                if (!name.equals(HttpHeaders.ContentType, ignoreCase = true)) header(name, value)
            }
            // Content-Type travels with the body; Content-Length comes from its size.
            setBody(ByteArrayContent(file.bytes, ContentType.parse(file.contentType)))
        }
        if (response.status.isSuccess()) {
            DataResult.Success(slot.objectKey)
        } else {
            DataResult.Failure(DataError.Api(ApiErrorCode.INTERNAL_ERROR, "The upload didn't go through. Try again."))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (timeout: SocketTimeoutException) {
        DataResult.Failure(DataError.Timeout)
    } catch (timeout: ConnectTimeoutException) {
        DataResult.Failure(DataError.Timeout)
    } catch (io: IOException) {
        DataResult.Failure(DataError.Offline)
    } catch (throwable: Throwable) {
        DataResult.Failure(DataError.Unknown(throwable.message ?: "Upload failed"))
    }
}

/** A bare client for presigned storage URLs: no auth, no base URL, no JSON. */
fun storageHttpClient(): HttpClient = HttpClient(createHttpClientEngine()) {
    expectSuccess = false
    install(HttpTimeout) {
        // A 10 MB receipt over a weak mobile connection needs far longer than an API call.
        requestTimeoutMillis = 120_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
}
