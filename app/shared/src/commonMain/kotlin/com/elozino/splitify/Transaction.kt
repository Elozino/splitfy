package com.elozino.splitify

import com.elozino.splitify.domain.model.LedgerEntry
import com.elozino.splitify.domain.model.LedgerEntryType

/**
 * A statement row as the history screen renders it.
 *
 * Built from a [LedgerEntry] — the row the backend wrote when money actually moved —
 * so the statement can never disagree with the balance.
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val title: String,
    val subtitle: String,
    val amount: String,
    val date: String,
    val balanceAfter: String,
    val brandIcon: String? = null,
    val notes: String? = null,
) {
    companion object {
        fun from(entry: LedgerEntry): Transaction {
            val timestamp = entry.occurredAt.toString()
            val day = timestamp.take(10)
            val time = timestamp.drop(11).take(5)
            return Transaction(
                id = entry.id,
                type = entry.type.toTransactionType(),
                title = entry.counterpartyName ?: entry.description,
                subtitle = "$day | $time",
                // Signed for display: credits add, debits subtract.
                amount = (if (entry.type.isCredit) "+ " else "- ") +
                    entry.amount.toPlainString() + " " + entry.amount.currency.code,
                date = "$day $time",
                balanceAfter = entry.balanceAfter.toPlainString(),
                brandIcon = entry.paymentMethodBrand?.let { "drawable/ic_$it.svg" },
                notes = entry.note,
            )
        }
    }
}

private fun LedgerEntryType.toTransactionType(): TransactionType = when (this) {
    LedgerEntryType.TOP_UP -> TransactionType.TOPUP
    LedgerEntryType.WITHDRAWAL -> TransactionType.WITHDRAW
    LedgerEntryType.TRANSFER_OUT -> TransactionType.PAY
    LedgerEntryType.TRANSFER_IN -> TransactionType.REQUEST
    LedgerEntryType.EXPENSE_SETTLEMENT -> TransactionType.EXPENSE
}
