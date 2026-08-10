package com.elozino.splitify.api.dto

import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.UserId
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Cursor pagination. Offsets drift when rows are inserted mid-scroll; an opaque
 * cursor keeps an infinite list stable while new expenses are being added.
 */
@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = nextCursor != null,
) {
    companion object {
        const val DEFAULT_LIMIT = 25
        const val MAX_LIMIT = 100
    }
}

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val avatarUrl: String? = null,
    val defaultCurrency: CurrencyCode? = null,
)

@Serializable
enum class ActivityType {
    EXPENSE_ADDED, EXPENSE_UPDATED, EXPENSE_DELETED,
    SETTLEMENT_CREATED, SETTLEMENT_CONFIRMED,
    MEMBER_JOINED, MEMBER_LEFT, GROUP_CREATED,
}

@Serializable
data class ActivityItem(
    val id: String,
    val type: ActivityType,
    val groupId: String?,
    val groupName: String?,
    val actorId: UserId,
    val actorName: String,
    val summary: String,
    val occurredAt: Instant,
)
