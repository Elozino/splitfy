package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.FieldError
import com.elozino.splitfy.api.dto.UploadConfig
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.api.dto.UploadSlot
import com.elozino.splitfy.api.dto.UploadSlotRequest
import com.elozino.splitfy.backend.repositories.NewUpload
import com.elozino.splitfy.backend.repositories.UploadRepository
import com.elozino.splitfy.backend.storage.ObjectKeys
import com.elozino.splitfy.backend.storage.ObjectStorage
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.GroupRole
import java.util.UUID

/**
 * Hands out upload slots: presigned PUT URLs for one object each.
 *
 * Every key issued is recorded against its group and purpose, and that record is
 * the only thing an expense receipt or a group cover may later point at — see
 * [requireIssuedUpload].
 */
class UploadService(
    private val uploads: UploadRepository,
    private val storage: ObjectStorage,
    private val groupService: GroupService,
) {

    fun config() = UploadConfig(enabled = storage.isEnabled)

    suspend fun requestSlot(groupId: UUID, request: UploadSlotRequest, callerId: UUID): UploadSlot {
        // Membership first, so a non-member learns nothing, not even that uploads are off.
        val (_, role) = groupService.requireMembership(groupId, callerId)
        if (request.purpose == UploadPurpose.GROUP_COVER && role !in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
            throw AppException.Forbidden("Only an owner or admin can change the cover photo")
        }

        val contentType = request.contentType.substringBefore(';').trim().lowercase()
        val errors = buildList {
            if (contentType !in UploadRules.allowedContentTypes(request.purpose)) {
                val allowed = UploadRules.allowedContentTypes(request.purpose).sorted().joinToString(", ")
                add(FieldError("contentType", "Upload one of: $allowed"))
            }
            if (request.sizeBytes <= 0) {
                add(FieldError("sizeBytes", "The file is empty"))
            } else if (request.sizeBytes > UploadRules.MAX_BYTES) {
                add(FieldError("sizeBytes", "Files can be at most ${UploadRules.MAX_BYTES / (1024 * 1024)} MB"))
            }
        }
        if (errors.isNotEmpty()) throw AppException.Validation(errors)
        if (!storage.isEnabled) throw ObjectStorage.unavailable()

        val key = ObjectKeys.newKey(groupId, request.purpose, contentType)
        val signed = storage.presignUpload(key, contentType, request.sizeBytes)
        uploads.record(
            NewUpload(
                objectKey = key,
                groupId = groupId,
                purpose = request.purpose,
                contentType = contentType,
                sizeBytes = request.sizeBytes,
                uploadedBy = callerId,
            )
        )
        return UploadSlot(
            uploadUrl = signed.url,
            objectKey = key,
            expiresAt = signed.expiresAt,
            headers = signed.headers,
        )
    }
}

/**
 * Accepts [key] only if this server issued it for [groupId] and [purpose].
 *
 * This is what stops a client from attaching an arbitrary URL (a tracking pixel, a
 * phishing page) or another group's receipt to its own expense.
 */
internal suspend fun UploadRepository.requireIssuedUpload(
    key: String,
    groupId: UUID,
    purpose: UploadPurpose,
    field: String,
) {
    if (!ObjectKeys.belongsTo(key, groupId) || !wasIssued(key, groupId, purpose)) {
        throw AppException.Validation(listOf(FieldError(field, "Upload the file through splitfy, then attach it")))
    }
}
