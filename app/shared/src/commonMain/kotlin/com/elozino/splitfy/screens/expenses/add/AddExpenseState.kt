package com.elozino.splitfy.screens.expenses.add

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

enum class AddExpenseStep { DETAILS, SPLIT_TYPE, CAMERA }

data class AddExpenseState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val title: String = "",
    val amountInput: String = "",
    val notes: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val paidBy: UserId? = null,
    val participantIds: Set<String> = emptySet(),
    val weights: Map<String, Long> = emptyMap(),
    val receiptUrl: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
    val step: AddExpenseStep = AddExpenseStep.DETAILS,
    val isImageSheetVisible: Boolean = false,
) {
    val currency: CurrencyCode get() = group?.currency ?: CurrencyCode.NGN
    val members: List<GroupMember> get() = group?.members.orEmpty()
    val amount: Money? get() = Money.parse(amountInput, currency)
    val participants: List<UserId>
        get() = members.map { it.userId }.filter { it.value in participantIds }
    fun isParticipant(userId: UserId): Boolean = userId.value in participantIds
    fun weightOf(userId: UserId): Long = weights[userId.value] ?: 0L
    val canSave: Boolean
        get() = title.isNotBlank() && amount?.isPositive == true &&
            participants.isNotEmpty() && paidBy != null && !isSaving && splitError == null
    val previewShares: List<ExpenseShare>
        get() = when (val result = outcome) {
            is SplitOutcome.Success -> result.shares
            else -> emptyList()
        }
    val splitError: String? get() = (outcome as? SplitOutcome.Failure)?.error?.describe()
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
    is SplitError.ExactAmountsDoNotSumToTotal -> "Shares add up to $actual, the total is $expected"
    is SplitError.PercentagesDoNotSumTo100 ->
        "Percentages total ${actualBasisPoints / 100.0}%, they must be 100%"
    is SplitError.NonPositiveShare -> "Every participant needs at least one share"
    is SplitError.NegativeAmount -> "Amounts cannot be negative"
}

