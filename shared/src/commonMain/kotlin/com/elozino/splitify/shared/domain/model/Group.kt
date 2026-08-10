package com.elozino.splitify.shared.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: UserId,
    val email: String,
    val displayName: String,
    val phoneNumber: String? = null,
    val avatarUrl: String? = null,
    val defaultCurrency: CurrencyCode = CurrencyCode.NGN,
    val createdAt: Instant,
)

@Serializable
enum class GroupRole { OWNER, ADMIN, MEMBER }

@Serializable
data class GroupMember(
    val userId: UserId,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: GroupRole = GroupRole.MEMBER,
    val joinedAt: Instant,
)

@Serializable
enum class GroupCategory { TRIP, HOME, COUPLE, EVENT, PROJECT, OTHER }

@Serializable
data class Group(
    val id: GroupId,
    val name: String,
    val description: String? = null,
    val category: GroupCategory = GroupCategory.OTHER,
    val currency: CurrencyCode,
    val coverImageUrl: String? = null,
    val members: List<GroupMember> = emptyList(),
    val createdBy: UserId,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean get() = archivedAt != null

    fun memberOrNull(userId: UserId): GroupMember? = members.firstOrNull { it.userId == userId }

    fun canManage(userId: UserId): Boolean =
        memberOrNull(userId)?.role in setOf(GroupRole.OWNER, GroupRole.ADMIN)
}
