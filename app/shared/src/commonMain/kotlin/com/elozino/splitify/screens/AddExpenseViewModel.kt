package com.elozino.splitify.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.api.dto.CreateExpenseRequest
import com.elozino.splitify.api.dto.SplitPayload
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.ExpenseCategory
import com.elozino.splitify.domain.model.ExpenseShare
import com.elozino.splitify.domain.model.Group
import com.elozino.splitify.domain.model.GroupMember
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.SplitMethod
import com.elozino.splitify.domain.model.UserId
import com.elozino.splitify.domain.split.SplitCalculator
import com.elozino.splitify.domain.split.SplitError
import com.elozino.splitify.domain.split.SplitInput
import com.elozino.splitify.domain.split.SplitOutcome
import com.elozino.splitify.repositories.ExpenseRepository
import com.elozino.splitify.repositories.GroupRepository
import com.elozino.splitify.SessionManager
import com.elozino.splitify.userMessage
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddExpenseUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val title: String = "",
    val amountInput: String = "",
    val notes: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val paidBy: UserId? = null,
    val participantIds: Set<String> = emptySet(),
    /** Per-participant input: exact minor units, basis points, or share counts. */
    val weights: Map<String, Long> = emptyMap(),
    val receiptUrl: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
) {
    val currency: CurrencyCode get() = group?.currency ?: CurrencyCode.NGN

    val members: List<GroupMember> get() = group?.members.orEmpty()

    val amount: Money? get() = Money.parse(amountInput, currency)

    val participants: List<UserId>
        get() = members.map { it.userId }.filter { it.value in participantIds }

    fun isParticipant(userId: UserId): Boolean = userId.value in participantIds

    fun weightOf(userId: UserId): Long = weights[userId.value] ?: 0L

    val canSave: Boolean
        get() = title.isNotBlank() &&
            amount?.isPositive == true &&
            participants.isNotEmpty() &&
            paidBy != null &&
            !isSaving &&
            splitError == null

    /** Live preview computed with the same code the server will run. */
    val previewShares: List<ExpenseShare>
        get() = when (val outcome = outcome) {
            is SplitOutcome.Success -> outcome.shares
            else -> emptyList()
        }

    val splitError: String?
        get() = (outcome as? SplitOutcome.Failure)?.error?.describe()

    private val outcome: SplitOutcome?
        get() {
            val total = amount ?: return null
            if (participants.isEmpty()) return null
            return SplitCalculator.calculate(total, toSplitInput())
        }

    fun toSplitInput(): SplitInput = when (splitMethod) {
        SplitMethod.EQUAL -> SplitInput.Equal(participants)
        SplitMethod.EXACT -> SplitInput.Exact(participants.associateWith { weightOf(it) })
        SplitMethod.PERCENTAGE ->
            SplitInput.Percentage(participants.associateWith { weightOf(it).toInt() })
        SplitMethod.SHARES ->
            SplitInput.Shares(participants.associateWith { weightOf(it).toInt() })
    }

    /** How much of the total is still unassigned, for the footer hint. */
    val remaining: Long
        get() = when (splitMethod) {
            SplitMethod.EXACT -> (amount?.amount ?: 0L) - participants.sumOf { weightOf(it) }
            SplitMethod.PERCENTAGE ->
                SplitCalculator.ONE_HUNDRED_PERCENT_BPS - participants.sumOf { weightOf(it) }
            else -> 0L
        }
}

private fun SplitError.describe(): String = when (this) {
    SplitError.NoParticipants -> "Select at least one participant"
    SplitError.NonPositiveTotal -> "Enter an amount greater than zero"
    is SplitError.DuplicateParticipant -> "Someone was added twice"
    is SplitError.ExactAmountsDoNotSumToTotal ->
        "Shares add up to $actual, the total is $expected"
    is SplitError.PercentagesDoNotSumTo100 ->
        "Percentages total ${actualBasisPoints / 100.0}%, they must be 100%"
    is SplitError.NonPositiveShare -> "Every participant needs at least one share"
    is SplitError.NegativeAmount -> "Amounts cannot be negative"
}

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

    private val _state = MutableStateFlow(AddExpenseUiState())
    val state: StateFlow<AddExpenseUiState> = _state.asStateFlow()

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
