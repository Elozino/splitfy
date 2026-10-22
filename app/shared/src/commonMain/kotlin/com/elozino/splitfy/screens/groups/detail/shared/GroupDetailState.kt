package com.elozino.splitfy.screens.groups.detail.shared

import com.elozino.splitfy.domain.model.Balance
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupMember
import com.elozino.splitfy.domain.model.GroupRole
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SuggestedTransfer

internal data class ExpenseItemData(
    val title: String,
    val subtitle: String,
    val amount: String,
    val date: String,
    val emoji: String,
)

data class GroupDetailState(
    val isLoading: Boolean = true,
    /** A pull-to-refresh is in flight; content stays on screen meanwhile. */
    val isRefreshing: Boolean = false,
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
    /** Why the group couldn't be loaded at all. Unlike [errorMessage], not cleared by the snackbar. */
    val loadError: String? = null,
    val isAddMemberDialogVisible: Boolean = false,
    val memberEmail: String = "",
    val memberError: String? = null,
    val isUpdatingMembers: Boolean = false,
    /** Whether the server accepts uploads; without storage the cover edit button is hidden. */
    val uploadsEnabled: Boolean = false,
    val isUploadingCover: Boolean = false,
    val openingReceiptFor: String? = null,
    /** A freshly signed receipt link waiting to be opened; consumed once shown. */
    val receiptLinkToOpen: String? = null,
) {
    /** Owners and admins may add and remove members; the server enforces the same rule. */
    val canManageMembers: Boolean
        get() = group?.members?.firstOrNull { it.userId.value == currentUserId }?.role
            ?.let { it == GroupRole.OWNER || it == GroupRole.ADMIN } == true

    /** Changing the cover follows the same rule as editing the group. */
    val canEditCover: Boolean
        get() = canManageMembers && uploadsEnabled && group?.isArchived == false

    /** Whether [member] can be removed by the viewer: never the owner, never yourself (leave instead). */
    fun canRemove(member: GroupMember): Boolean =
        canManageMembers && member.role != GroupRole.OWNER && member.userId.value != currentUserId

    val hasMore: Boolean get() = nextCursor != null

    /** Which actions the bottom bar offers, from where the group's money stands. */
    val actions: GroupActions
        get() = when {
            group == null -> GroupActions.None
            expenses.isEmpty() -> GroupActions.AddOnly
            suggestedTransfers.isEmpty() -> GroupActions.SettledUp
            else -> GroupActions.AddAndSettle
        }

    /** Nothing to show yet: the screen draws placeholders rather than zeroes. */
    val isFirstLoad: Boolean get() = isLoading && group == null
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

enum class GroupActions {
    /** Still loading, or the group couldn't be loaded: no actions yet. */
    None,

    /** No expenses yet, so there is nothing to settle. */
    AddOnly,

    /** Someone owes someone. */
    AddAndSettle,

    /** Expenses exist and nobody owes anything: celebrate, keep a way to add more. */
    SettledUp,
}
