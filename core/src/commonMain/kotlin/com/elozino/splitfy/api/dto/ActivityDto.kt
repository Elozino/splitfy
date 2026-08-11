package com.elozino.splitfy.api.dto

import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.UserId
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * One "A owes B" line on the activity screen.
 *
 * Derived from the ledger every time it is requested rather than stored, so it can
 * never disagree with the group balances.
 */
@Serializable
data class DebtEntry(
    val fromUserId: UserId,
    val fromName: String,
    val toUserId: UserId,
    val toName: String,
    val amount: Money,
    val groupId: GroupId,
    val groupName: String,
    val isSettled: Boolean = false,
    val lastActivityAt: Instant,
) {
    /** True when the signed-in user is the one who is owed. */
    fun isOwedTo(userId: UserId): Boolean = toUserId == userId
}

@Serializable
enum class ActivityFilter { ALL, YOU_OWE, OWED_TO_YOU }

@Serializable
data class ActivityResponse(
    val entries: List<DebtEntry>,
    val totalYouOwe: Money,
    val totalOwedToYou: Money,
)

@Serializable
enum class NotificationCategory { SYSTEM, SECURITY, PAYMENT, GROUP, PROMOTION }

@Serializable
data class AppNotification(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: Instant,
)

@Serializable
data class NotificationsResponse(
    val items: List<AppNotification>,
    val unreadCount: Int,
)

/**
 * Someone the user shares a group with.
 *
 * [netBalance] is the sum across every shared group: positive means they owe the
 * signed-in user.
 */
@Serializable
data class Contact(
    val userId: UserId,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    val phoneNumber: String? = null,
    val isFavorite: Boolean = false,
    val sharedGroupCount: Int = 0,
    val netBalance: Money? = null,
)

@Serializable
data class ContactsResponse(
    val contacts: List<Contact>,
    val favorites: List<Contact>,
)
