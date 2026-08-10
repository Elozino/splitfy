package com.elozino.splitify.shared.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class SplitMethod {
    /** Everyone owes the same amount; leftover minor units go to the first participants. */
    EQUAL,

    /** The caller supplies each participant's exact amount; they must sum to the total. */
    EXACT,

    /** The caller supplies basis points (1% = 100); they must sum to 10 000. */
    PERCENTAGE,

    /** The caller supplies share counts (e.g. 2 shares for a couple, 1 for a single). */
    SHARES,
}

/** One participant's slice of an expense. */
@Serializable
data class ExpenseShare(
    val userId: UserId,
    val amount: Money,
    /** The raw input that produced [amount]: exact minor units, basis points or share count. */
    val weight: Long = 0,
)

@Serializable
enum class ExpenseCategory {
    FOOD, TRANSPORT, ACCOMMODATION, GROCERIES, ENTERTAINMENT, UTILITIES, SHOPPING, HEALTH, OTHER
}

@Serializable
data class Expense(
    val id: ExpenseId,
    val groupId: GroupId,
    val description: String,
    val total: Money,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val paidBy: UserId,
    val splitMethod: SplitMethod,
    val shares: List<ExpenseShare>,
    val receiptUrl: String? = null,
    val notes: String? = null,
    val incurredAt: Instant,
    val createdBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    fun shareOf(userId: UserId): Money =
        shares.firstOrNull { it.userId == userId }?.amount ?: Money.zero(total.currency)
}

@Serializable
enum class SettlementStatus { PENDING, CONFIRMED, CANCELLED }

/** A payment from one member to another that reduces an outstanding balance. */
@Serializable
data class Settlement(
    val id: SettlementId,
    val groupId: GroupId,
    val fromUserId: UserId,
    val toUserId: UserId,
    val amount: Money,
    val status: SettlementStatus = SettlementStatus.PENDING,
    val note: String? = null,
    val settledAt: Instant,
    val createdBy: UserId,
    val createdAt: Instant,
)

/** What a single member is owed (positive) or owes (negative) inside a group. */
@Serializable
data class Balance(
    val userId: UserId,
    val net: Money,
) {
    val isOwed: Boolean get() = net.isPositive
    val owes: Boolean get() = net.isNegative
}

/** A suggested payment produced by the debt simplifier. */
@Serializable
data class SuggestedTransfer(
    val from: UserId,
    val to: UserId,
    val amount: Money,
)
