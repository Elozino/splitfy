package com.elozino.splitify

import com.elozino.splitify.domain.model.LedgerEntryType
import com.elozino.splitify.domain.model.TransferKind

/**
 * The statement filter chips.
 *
 * Each one maps onto the ledger entry types the backend actually writes, so the
 * filter cannot drift from what is stored.
 */
enum class TransactionType {
    ALL,
    PAY,
    REQUEST,
    TOPUP,
    WITHDRAW,
    EXPENSE;

    fun toLedgerTypes(): List<LedgerEntryType> = when (this) {
        ALL -> emptyList()
        PAY -> listOf(LedgerEntryType.TRANSFER_OUT)
        REQUEST -> listOf(LedgerEntryType.TRANSFER_IN)
        TOPUP -> listOf(LedgerEntryType.TOP_UP)
        WITHDRAW -> listOf(LedgerEntryType.WITHDRAWAL)
        EXPENSE -> listOf(LedgerEntryType.EXPENSE_SETTLEMENT)
    }

    /** Which direction the pay/request screen is operating in. */
    fun toTransferKind(): TransferKind =
        if (this == REQUEST) TransferKind.REQUEST else TransferKind.PAYMENT

    val label: String
        get() = when (this) {
            ALL -> "All"
            PAY -> "Pay"
            REQUEST -> "Request"
            TOPUP -> "Top Up"
            WITHDRAW -> "Withdraw"
            EXPENSE -> "Expense"
        }
}
