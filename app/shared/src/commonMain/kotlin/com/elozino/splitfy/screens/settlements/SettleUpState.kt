package com.elozino.splitfy.screens.settlements

import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SettlementStatus
import com.elozino.splitfy.domain.model.SuggestedTransfer
import com.elozino.splitfy.domain.model.UserId

/**
 * The payment being recorded in the sheet. [clientRequestId] is minted when the sheet
 * opens, so a retry after a timeout cannot record the same payment twice.
 */
data class PaymentDraft(
    val from: UserId,
    val to: UserId,
    val amountText: String,
    val note: String = "",
    val clientRequestId: String,
) {
    /** True when the current user is the payee, i.e. recording money they received. */
    fun isReceipt(currentUserId: String?) = to.value == currentUserId
}

data class SettleUpState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val currentUserId: String? = null,
    val suggestedTransfers: List<SuggestedTransfer> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    val draft: PaymentDraft? = null,
    val busySettlementIds: Set<String> = emptySet(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val currency: CurrencyCode get() = group?.currency ?: CurrencyCode.NGN

    fun nameOf(userId: UserId): String = when {
        userId.value == currentUserId -> "You"
        else -> group?.members?.firstOrNull { it.userId == userId }?.displayName ?: "Someone"
    }

    private fun involvesYou(from: UserId, to: UserId) =
        from.value == currentUserId || to.value == currentUserId

    private val pending: List<Settlement>
        get() = settlements.filter { it.status == SettlementStatus.PENDING }

    /** Payments someone says they sent you; only you can confirm them. */
    val awaitingYourConfirmation: List<Settlement>
        get() = pending.filter { it.toUserId.value == currentUserId }

    /** Payments you recorded that the payee has not confirmed yet. */
    val awaitingTheirConfirmation: List<Settlement>
        get() = pending.filter { it.fromUserId.value == currentUserId }

    /**
     * What is still left to pay after pending payments are taken off.
     *
     * Balances only count confirmed settlements, so without this a payment you just
     * recorded would keep showing as owed until the payee confirms it.
     */
    private val outstanding: List<SuggestedTransfer>
        get() = suggestedTransfers.mapNotNull { transfer ->
            val alreadyPending = pending
                .filter { it.fromUserId == transfer.from && it.toUserId == transfer.to }
                .fold(Money.zero(transfer.amount.currency)) { acc, s -> acc + s.amount }
            val left = transfer.amount - alreadyPending
            if (left.isPositive) transfer.copy(amount = left) else null
        }

    val yourTransfers: List<SuggestedTransfer>
        get() = outstanding.filter { involvesYou(it.from, it.to) }

    val otherTransfers: List<SuggestedTransfer>
        get() = outstanding.filterNot { involvesYou(it.from, it.to) }

    val history: List<Settlement>
        get() = settlements
            .filter { it.status == SettlementStatus.CONFIRMED }
            .sortedByDescending { it.settledAt }

    val isAllSettled: Boolean
        get() = !isLoading && suggestedTransfers.isEmpty() && pending.isEmpty()
}
