package com.elozino.splitfy.screens.expenses.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.SplitPayload
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.ExpenseShare
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupMember
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.domain.split.SplitCalculator
import com.elozino.splitfy.domain.split.SplitError
import com.elozino.splitfy.domain.split.SplitInput
import com.elozino.splitfy.domain.split.SplitOutcome
import com.elozino.splitfy.repositories.ExpenseRepository
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drafts a new expense.
 *
 * The split preview runs `:shared`'s [SplitCalculator] on-device, so the amounts
 * shown before saving are exactly the amounts the server stores — the client sends
 * inputs, never the computed shares.
 */
class AddExpenseViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AddExpenseState())
    val state: StateFlow<AddExpenseState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = groupRepository.get(groupId)) {
                is DataResult.Success -> _state.update { current ->
                    val me = sessionManager.currentUserId()
                    current.copy(
                        isLoading = false,
                        group = result.data,
                        // Default to an equal split between everyone, paid by the viewer.
                        participantIds = result.data.members.map { it.userId.value }.toSet(),
                        paidBy = me?.let(::UserId) ?: result.data.members.firstOrNull()?.userId,
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value, errorMessage = null) }

    fun onAmountChange(value: String) = _state.update { it.copy(amountInput = value, errorMessage = null) }

    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    fun onCategoryChange(category: ExpenseCategory) = _state.update { it.copy(category = category) }

    fun onPaidByChange(userId: UserId) = _state.update { it.copy(paidBy = userId) }

    fun onSplitMethodChange(method: SplitMethod) = _state.update { current ->
        // Switching method invalidates the old inputs; start the new one clean.
        current.copy(
            splitMethod = method,
            weights = when (method) {
                SplitMethod.SHARES -> current.participantIds.associateWith { 1L }
                else -> emptyMap()
            },
        )
    }

    fun toggleParticipant(userId: UserId) = _state.update { current ->
        val id = userId.value
        val ids = if (id in current.participantIds) current.participantIds - id
        else current.participantIds + id
        current.copy(
            participantIds = ids,
            weights = current.weights.filterKeys { it in ids },
        )
    }

    fun onWeightChange(userId: UserId, raw: String) = _state.update { current ->
        val parsed = when (current.splitMethod) {
            // Exact amounts are typed in major units; percentages in whole percent.
            SplitMethod.EXACT -> Money.parse(raw, current.currency)?.amount
            SplitMethod.PERCENTAGE -> raw.toDoubleOrNull()?.let { (it * 100).toLong() }
            else -> raw.toLongOrNull()
        } ?: 0L
        current.copy(weights = current.weights + (userId.value to parsed))
    }

    fun onReceiptCaptured(url: String) = _state.update { it.copy(receiptUrl = url) }

    fun navigateTo(step: AddExpenseStep) = _state.update { it.copy(step = step) }

    fun setImageSheetVisible(isVisible: Boolean) =
        _state.update { it.copy(isImageSheetVisible = isVisible) }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    fun save(clientRequestId: String) {
        val current = _state.value
        val total = current.amount ?: return
        val payer = current.paidBy ?: return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val result = expenseRepository.create(
                groupId,
                CreateExpenseRequest(
                    description = current.title.trim(),
                    total = total,
                    category = current.category,
                    paidBy = payer,
                    split = SplitPayload.from(current.toSplitInput()),
                    receiptUrl = current.receiptUrl,
                    notes = current.notes.takeIf { it.isNotBlank() },
                    clientRequestId = clientRequestId,
                ),
            )
            when (result) {
                is DataResult.Success -> _state.update { it.copy(isSaving = false, saved = true) }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }
}

