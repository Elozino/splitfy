package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.schema.UploadsTable
import java.util.UUID
import kotlin.time.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

data class NewUpload(
    val objectKey: String,
    /** Null for a profile photo. */
    val groupId: UUID?,
    val purpose: UploadPurpose,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedBy: UUID,
)

class UploadRepository {

    suspend fun record(upload: NewUpload) = dbQuery {
        UploadsTable.insert {
            it[objectKey] = upload.objectKey
            it[groupId] = upload.groupId
            it[purpose] = upload.purpose
            it[contentType] = upload.contentType
            it[sizeBytes] = upload.sizeBytes
            it[uploadedBy] = upload.uploadedBy
            it[createdAt] = Clock.System.now().toJava()
        }
        Unit
    }

    /** Whether [objectKey] was issued for [groupId] and [purpose]. */
    suspend fun wasIssued(objectKey: String, groupId: UUID, purpose: UploadPurpose): Boolean = dbQuery {
        UploadsTable.selectAll().where {
            (UploadsTable.objectKey eq objectKey) and
                (UploadsTable.groupId eq groupId) and
                (UploadsTable.purpose eq purpose)
        }.any()
    }

    /** Whether [objectKey] was issued to [userId] as a group-less upload for [purpose]. */
    suspend fun wasIssuedToUser(objectKey: String, userId: UUID, purpose: UploadPurpose): Boolean = dbQuery {
        UploadsTable.selectAll().where {
            (UploadsTable.objectKey eq objectKey) and
                UploadsTable.groupId.isNull() and
                (UploadsTable.uploadedBy eq userId) and
                (UploadsTable.purpose eq purpose)
        }.any()
    }
}
