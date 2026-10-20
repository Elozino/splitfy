package com.elozino.splitfy.backend.storage

import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.backend.utils.TokenGenerator
import java.util.UUID

/**
 * Object key layout: `groups/<groupId>/<folder>/<random>.<ext>`.
 *
 * The group prefix keeps one group's files together (and deletable together); the
 * 192-bit random name makes a key unguessable, so knowing one key says nothing about
 * any other.
 */
object ObjectKeys {
    private const val ROOT = "groups"

    fun newKey(groupId: UUID, purpose: UploadPurpose, contentType: String): String {
        val folder = when (purpose) {
            UploadPurpose.RECEIPT -> "receipts"
            UploadPurpose.GROUP_COVER -> "covers"
        }
        return "$ROOT/$groupId/$folder/${TokenGenerator.opaqueToken(24)}.${extension(contentType)}"
    }

    /** Whether [key] looks like one of ours, as opposed to a legacy or foreign URL. */
    fun isOurs(key: String): Boolean = key.startsWith("$ROOT/")

    fun belongsTo(key: String, groupId: UUID): Boolean = key.startsWith("$ROOT/$groupId/")

    private fun extension(contentType: String) = when (contentType) {
        UploadRules.JPEG -> "jpg"
        UploadRules.PNG -> "png"
        UploadRules.HEIC -> "heic"
        UploadRules.PDF -> "pdf"
        else -> "bin"
    }
}
