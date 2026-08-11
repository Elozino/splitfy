package com.elozino.splitify.screens

import com.elozino.splitify.TransactionType
import com.elozino.splitify.api.dto.Contact
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.LedgerEntry
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.Transfer

data class TransactionState(
    val isLoading: Boolean = true,
    val balance: Money = Money.zero(CurrencyCode.NGN),
    val contacts: List<Contact> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(),
    val pendingRequests: List<Transfer> = emptyList(),
    val filter: TransactionType = TransactionType.ALL,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val isSubmitting: Boolean = false,
    val lastTransfer: Transfer? = null,
    val errorMessage: String? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
}
