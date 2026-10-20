package com.elozino.splitfy.api.dto

import com.elozino.splitfy.domain.model.CurrencyCode
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** A group's shareable link. [url] opens the app when installed, a web page otherwise. */
@Serializable
data class GroupInvite(
    val token: String,
    val url: String,
    val expiresAt: Instant,
)

/** What someone holding a link may see before joining. Deliberately little. */
@Serializable
data class InvitePreview(
    val groupId: String,
    val groupName: String,
    val memberCount: Int,
    val invitedBy: String,
    val currency: CurrencyCode,
)
