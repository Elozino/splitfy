package com.elozino.splitfy.backend.storage

import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.backend.utils.TokenGenerator
import java.util.UUID

/**
 * Object key layout: `groups/<groupId>/<folder>/<random>.<ext>`, and
 * `users/<userId>/avatars/<random>.<ext>` for profile photos.
 *
 * The group prefix keeps one group's files together (and deletable together); the
 * 192-bit random name makes a key unguessable, so knowing one key says nothing about
 * any other.
 */
object ObjectKeys {
    private const val ROOT = "groups"
    private const val USER_ROOT = "users"

    fun newKey(groupId: UUID, purpose: UploadPurpose, contentType: String): String {
        val folder = when (purpose) {
            UploadPurpose.RECEIPT -> "receipts"
            UploadPurpose.GROUP_COVER -> "covers"
            UploadPurpose.AVATAR -> error("A profile photo belongs to a user; use newAvatarKey")
        }
        return "$ROOT/$groupId/$folder/${TokenGenerator.opaqueToken(24)}.${extension(contentType)}"
    }

    fun newAvatarKey(userId: UUID, contentType: String): String =
        "$USER_ROOT/$userId/avatars/${TokenGenerator.opaqueToken(24)}.${extension(contentType)}"

    /** Whether [key] looks like one of ours, as opposed to a legacy or foreign URL. */
    fun isOurs(key: String): Boolean = key.startsWith("$ROOT/") || key.startsWith("$USER_ROOT/")

    fun belongsTo(key: String, groupId: UUID): Boolean = key.startsWith("$ROOT/$groupId/")

    fun belongsToUser(key: String, userId: UUID): Boolean = key.startsWith("$USER_ROOT/$userId/")

    private fun extension(contentType: String) = when (contentType) {
        UploadRules.JPEG -> "jpg"
        UploadRules.PNG -> "png"
        UploadRules.HEIC -> "heic"
        UploadRules.PDF -> "pdf"
        else -> "bin"
    }
}
