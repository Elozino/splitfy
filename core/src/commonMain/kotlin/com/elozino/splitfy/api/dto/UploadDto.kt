package com.elozino.splitfy.api.dto

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** What an upload is for. It decides who may request a slot and which file types are allowed. */
@Serializable
enum class UploadPurpose {
    /** A receipt attached to an expense. Any member may upload one; only members can read it. */
    RECEIPT,

    /** The group's cover photo. Owners and admins only, matching who may edit the group. */
    GROUP_COVER,

    /** The caller's own profile photo, requested through `ApiRoutes.Users.ME_UPLOADS`. */
    AVATAR,
}

/**
 * The limits both sides enforce. The app checks them before asking for a slot so the
 * user gets an instant answer; the server checks them again, and signs the declared
 * type and size into the upload URL so storage rejects anything else.
 */
object UploadRules {
    const val MAX_BYTES: Long = 10L * 1024 * 1024

    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val HEIC = "image/heic"
    const val PDF = "application/pdf"

    fun allowedContentTypes(purpose: UploadPurpose): Set<String> = when (purpose) {
        UploadPurpose.RECEIPT -> setOf(JPEG, PNG, HEIC, PDF)
        UploadPurpose.GROUP_COVER, UploadPurpose.AVATAR -> setOf(JPEG, PNG, HEIC)
    }
}

@Serializable
data class UploadSlotRequest(
    val purpose: UploadPurpose,
    val contentType: String,
    /** The exact size of the file. It is signed into the URL, so the PUT must match it. */
    val sizeBytes: Long,
)

/**
 * A one-off, short-lived permission to PUT one file straight to object storage.
 *
 * The client sends the file to [uploadUrl] with exactly [headers] (plus the
 * Content-Length it declared), then hands [objectKey] back as the expense's
 * `receiptUrl` or the group's `coverImageUrl`.
 */
@Serializable
data class UploadSlot(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: Instant,
    val headers: Map<String, String> = emptyMap(),
)

/** A short-lived link to read a private file, such as a receipt. */
@Serializable
data class FileLink(
    val url: String,
    val expiresAt: Instant,
)

/** Whether uploads work on this server. The app hides upload controls when they don't. */
@Serializable
data class UploadConfig(
    val enabled: Boolean,
    val maxBytes: Long = UploadRules.MAX_BYTES,
)
