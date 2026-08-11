package com.elozino.splitfy.api.dto

import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SettlementStatus
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.domain.split.SplitInput
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Wire form of a split. The client sends the *inputs* (who, how much, what method)
 * and never the computed amounts, so the server stays the authority on rounding.
 */
@Serializable
data class SplitPayload(
    val method: SplitMethod,
    val participantIds: List<UserId> = emptyList(),
    /** EXACT: minor units per participant. */
    val exactAmounts: Map<String, Long> = emptyMap(),
    /** PERCENTAGE: basis points per participant (1% = 100). */
    val basisPoints: Map<String, Int> = emptyMap(),
    /** SHARES: share counts per participant. */
    val shares: Map<String, Int> = emptyMap(),
) {
    fun toSplitInput(): SplitInput = when (method) {
        SplitMethod.EQUAL -> SplitInput.Equal(participantIds)
        SplitMethod.EXACT -> SplitInput.Exact(exactAmounts.mapKeys { UserId(it.key) })
        SplitMethod.PERCENTAGE -> SplitInput.Percentage(basisPoints.mapKeys { UserId(it.key) })
        SplitMethod.SHARES -> SplitInput.Shares(shares.mapKeys { UserId(it.key) })
    }

    companion object {
        fun from(input: SplitInput): SplitPayload = when (input) {
            is SplitInput.Equal -> SplitPayload(SplitMethod.EQUAL, participantIds = input.participants)
            is SplitInput.Exact -> SplitPayload(
                SplitMethod.EXACT,
                exactAmounts = input.amounts.mapKeys { it.key.value },
            )
            is SplitInput.Percentage -> SplitPayload(
                SplitMethod.PERCENTAGE,
                basisPoints = input.basisPoints.mapKeys { it.key.value },
            )
            is SplitInput.Shares -> SplitPayload(
                SplitMethod.SHARES,
                shares = input.shares.mapKeys { it.key.value },
            )
        }
    }
}

@Serializable
data class CreateExpenseRequest(
    val description: String,
    val total: Money,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val paidBy: UserId,
    val split: SplitPayload,
    val receiptUrl: String? = null,
    val notes: String? = null,
    val incurredAt: Instant? = null,
    /**
     * Client-generated idempotency key. Retrying a request that timed out mid-flight
     * returns the original expense instead of creating a duplicate.
     */
    val clientRequestId: String? = null,
)

@Serializable
data class UpdateExpenseRequest(
    val description: String? = null,
    val total: Money? = null,
    val category: ExpenseCategory? = null,
    val paidBy: UserId? = null,
    val split: SplitPayload? = null,
    val receiptUrl: String? = null,
    val notes: String? = null,
    val incurredAt: Instant? = null,
)

@Serializable
data class CreateSettlementRequest(
    val fromUserId: UserId,
    val toUserId: UserId,
    val amount: Money,
    val note: String? = null,
    val settledAt: Instant? = null,
    val clientRequestId: String? = null,
)

@Serializable
data class UpdateSettlementStatusRequest(
    val status: SettlementStatus,
)
