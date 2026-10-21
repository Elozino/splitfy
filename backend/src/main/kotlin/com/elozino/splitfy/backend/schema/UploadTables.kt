package com.elozino.splitfy.backend.schema

import com.elozino.splitfy.api.dto.UploadPurpose
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/** Upload slots this server has issued; see `V6__uploads.sql`. */
object UploadsTable : Table("uploads") {
    val objectKey = varchar("object_key", 255)
    /** Null for a profile photo, which belongs to [uploadedBy] alone. */
    val groupId = uuid("group_id").references(GroupsTable.id, onDelete = ReferenceOption.CASCADE).nullable()
    val purpose = enumerationByName("purpose", 16, UploadPurpose::class)
    val contentType = varchar("content_type", 64)
    val sizeBytes = long("size_bytes")
    val uploadedBy = uuid("uploaded_by").references(UsersTable.id)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(objectKey)

    init {
        index("ix_uploads_group", false, groupId)
    }
}
