package com.elozino.splitfy.screens.groups.detail.shared

import com.elozino.splitfy.domain.model.Balance
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SuggestedTransfer
import com.elozino.splitfy.screens.groups.detail.main.emoji

internal data class ExpenseItemData(
    val title: String,
    val subtitle: String,
    val amount: String,
    val date: String,
    val emoji: String,
)

data class GroupDetailState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val expenses: List<Expense> = emptyList(),
    val balances: List<Balance> = emptyList(),
    val suggestedTransfers: List<SuggestedTransfer> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val currentUserId: String? = null,
    val selectedTab: Int = 0,
    val isOverflowMenuVisible: Boolean = false,
) {
    val hasMore: Boolean get() = nextCursor != null
    fun yourBalance(userId: String?): Money? =
        balances.firstOrNull { it.userId.value == userId }?.net
    val currency get() = group?.currency ?: CurrencyCode.NGN
    val totalSpend: Money
        get() = expenses.fold(Money.zero(currency)) { acc, expense -> acc + expense.total }
    val yourShare: Money
        get() = expenses.fold(Money.zero(currency)) { acc, expense ->
            acc + (expense.shares.firstOrNull { it.userId.value == currentUserId }?.amount
                ?: Money.zero(currency))
        }
    val yourPayments: Money
        get() = expenses.filter { it.paidBy.value == currentUserId }
            .fold(Money.zero(currency)) { acc, expense -> acc + expense.total }
}

