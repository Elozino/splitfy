package com.elozino.splitify.feature.split.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.core.domain.DataResult
import com.elozino.splitify.core.domain.repository.ExpenseRepository
import com.elozino.splitify.core.domain.repository.GroupRepository
import com.elozino.splitify.core.domain.session.SessionManager
import com.elozino.splitify.core.domain.userMessage
import com.elozino.splitify.shared.api.dto.CreateExpenseRequest
import com.elozino.splitify.shared.api.dto.SplitPayload
import com.elozino.splitify.shared.domain.model.Balance
import com.elozino.splitify.shared.domain.model.Expense
import com.elozino.splitify.shared.domain.model.ExpenseCategory
import com.elozino.splitify.shared.domain.model.ExpenseShare
import com.elozino.splitify.shared.domain.model.Group
import com.elozino.splitify.shared.domain.model.Money
import com.elozino.splitify.shared.domain.model.SuggestedTransfer
import com.elozino.splitify.shared.domain.model.UserId
import com.elozino.splitify.shared.domain.split.SplitCalculator
import com.elozino.splitify.shared.domain.split.SplitInput
import com.elozino.splitify.shared.domain.split.SplitOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupDetailUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val expenses: List<Expense> = emptyList(),
    val balances: List<Balance> = emptyList(),
    val suggestedTransfers: List<SuggestedTransfer> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    /** Lets pure composables label the viewer's own rows "You". */
    val currentUserId: String? = null,
) {
    val hasMore: Boolean get() = nextCursor != null

    fun yourBalance(userId: String?): Money? =
        balances.firstOrNull { it.userId.value == userId }?.net

    val currency get() = group?.currency ?: com.elozino.splitify.shared.domain.model.CurrencyCode.NGN

    /** Total spent by the whole group. */
    val totalSpend: Money
        get() = expenses.fold(Money.zero(currency)) { acc, expense -> acc + expense.total }

    /** What the signed-in user personally consumed. */
    val yourShare: Money
        get() = expenses.fold(Money.zero(currency)) { acc, expense ->
            acc + expense.shares
                .firstOrNull { it.userId.value == currentUserId }
                ?.amount.orZero(currency)
        }

    /** What the signed-in user actually paid out. */
    val yourPayments: Money
        get() = expenses.filter { it.paidBy.value == currentUserId }
            .fold(Money.zero(currency)) { acc, expense -> acc + expense.total }
}

private fun Money?.orZero(currency: com.elozino.splitify.shared.domain.model.CurrencyCode) =
    this ?: Money.zero(currency)

class GroupDetailViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupDetailUiState())
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    val currentUserId: String? get() = sessionManager.currentUserId()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val group = async { groupRepository.get(groupId) }
            val expenses = async { expenseRepository.list(groupId) }
            val balances = async { groupRepository.balances(groupId) }

            val groupResult = group.await()
            val expensesResult = expenses.await()
            val balancesResult = balances.await()

            _state.update { current ->
                current.copy(
                    isLoading = false,
                    group = (groupResult as? DataResult.Success)?.data ?: current.group,
                    expenses = (expensesResult as? DataResult.Success)?.data?.items ?: current.expenses,
                    nextCursor = (expensesResult as? DataResult.Success)?.data?.nextCursor,
                    balances = (balancesResult as? DataResult.Success)?.data?.balances ?: current.balances,
                    suggestedTransfers = (balancesResult as? DataResult.Success)?.data?.suggestedTransfers
                        ?: current.suggestedTransfers,
                    errorMessage = listOfNotNull(
                        (groupResult as? DataResult.Failure)?.error,
                        (expensesResult as? DataResult.Failure)?.error,
                        (balancesResult as? DataResult.Failure)?.error,
                    ).firstOrNull()?.userMessage(),
                    currentUserId = sessionManager.currentUserId(),
                )
            }
        }
    }

    /** Appends the next page; ignored while a page is already in flight. */
    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            when (val result = expenseRepository.list(groupId, cursor)) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoadingMore = false,
                        expenses = it.expenses + result.data.items,
                        nextCursor = result.data.nextCursor,
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoadingMore = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /**
     * Live preview of how an expense would divide, computed on-device with the same
     * `:shared` calculator the server uses. The amounts shown here are therefore the
     * amounts that will be stored — no "off by one kobo" surprise after saving.
     */
    fun previewSplit(total: Money, input: SplitInput): List<ExpenseShare>? =
        when (val outcome = SplitCalculator.calculate(total, input)) {
            is SplitOutcome.Success -> outcome.shares
            is SplitOutcome.Failure -> null
        }

    fun addExpense(
        description: String,
        total: Money,
        paidBy: UserId,
        split: SplitPayload,
        category: ExpenseCategory = ExpenseCategory.OTHER,
        notes: String? = null,
        clientRequestId: String? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val result = expenseRepository.create(
                groupId,
                CreateExpenseRequest(
                    description = description,
                    total = total,
                    category = category,
                    paidBy = paidBy,
                    split = split,
                    notes = notes,
                    clientRequestId = clientRequestId,
                ),
            )
            when (result) {
                is DataResult.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    // Balances are derived server-side; reload rather than guessing.
                    load()
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.delete(groupId, expenseId)) {
                is DataResult.Success -> load()
                is DataResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
}
