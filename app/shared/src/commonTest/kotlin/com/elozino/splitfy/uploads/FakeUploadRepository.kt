package com.elozino.splitfy.uploads

import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.FileLink
import com.elozino.splitfy.api.dto.UploadConfig
import com.elozino.splitfy.api.dto.UploadPurpose
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred

/** Records uploads and answers with whatever each test sets up. */
class FakeUploadRepository(var enabled: Boolean = true) : UploadRepository {

    data class Call(val groupId: String, val purpose: UploadPurpose, val bytes: ByteArray, val contentType: String)

    val uploads = mutableListOf<Call>()
    var uploadResult: DataResult<String> = DataResult.Success("groups/g/receipts/key.jpg")
    var receiptResult: DataResult<FileLink> =
        DataResult.Success(FileLink("https://storage.example/receipt?sig=1", Instant.fromEpochSeconds(300)))

    /** When set, uploads wait for it, so a test can look at the in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun config() = DataResult.Success(UploadConfig(enabled = enabled))

    override suspend fun upload(groupId: String, purpose: UploadPurpose, file: UploadFile): DataResult<String> {
        uploads += Call(groupId, purpose, file.bytes, file.contentType)
        gate?.await()
        return uploadResult
    }

    override suspend fun uploadAvatar(file: UploadFile): DataResult<String> {
        uploads += Call("", UploadPurpose.AVATAR, file.bytes, file.contentType)
        gate?.await()
        return uploadResult
    }

    override suspend fun receiptLink(groupId: String, expenseId: String) = receiptResult
}

/** Tags the bytes so a test can tell compressed output from the original. */
val MarkingCompressor = ImageCompressor { bytes -> bytes + 0x7F }
