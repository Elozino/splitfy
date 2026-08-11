package com.elozino.splitify.backend.services

import com.elozino.splitify.api.ApiErrorCode
import com.elozino.splitify.api.dto.CreateExpenseRequest
import com.elozino.splitify.api.dto.UpdateExpenseRequest
import com.elozino.splitify.api.FieldError
import com.elozino.splitify.backend.repositories.ExpenseRepository
import com.elozino.splitify.backend.repositories.NewExpense
import com.elozino.splitify.backend.utils.AppException
import com.elozino.splitify.backend.utils.Cursor
import com.elozino.splitify.domain.model.Expense
import com.elozino.splitify.domain.model.ExpenseShare
import com.elozino.splitify.domain.model.Group
import com.elozino.splitify.domain.model.GroupRole
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.UserId
import com.elozino.splitify.domain.split.SplitCalculator
import com.elozino.splitify.domain.split.SplitError
import com.elozino.splitify.domain.split.SplitInput
import com.elozino.splitify.domain.split.SplitOutcome
import com.elozino.splitify.validation.Validators
import java.util.UUID
import kotlin.time.Clock

class ExpenseService(
    private val expenses: ExpenseRepository,
    private val groupService: GroupService,
) {

    suspend fun create(groupId: UUID, request: CreateExpenseRequest, callerId: UUID): Expense {
        val (group, _) = groupService.requireMembership(groupId, callerId)

        // Idempotency: a retried request returns the original expense.
        request.clientRequestId?.let { key ->
            expenses.findByClientRequestId(groupId, key)?.let { return it }
        }

        Validators.createExpense(request).let { if (it.isNotEmpty()) throw AppException.Validation(it) }
        requireGroupCurrency(group, request.total)

        val input = request.split.toSplitInput().withDefaultParticipants(group)
        requireParticipantsAreMembers(group, input)
        requirePayerIsMember(group, request.paidBy)

        val shares = computeShares(request.total, input)

        return expenses.create(
            NewExpense(
                groupId = groupId,
                description = request.description,
                total = request.total,
                category = request.category,
                paidBy = UUID.fromString(request.paidBy.value),
                splitMethod = input.method,
                shares = shares,
                receiptUrl = request.receiptUrl,
                notes = request.notes,
                incurredAt = request.incurredAt ?: Clock.System.now(),
                createdBy = callerId,
                clientRequestId = request.clientRequestId,
            )
        )
    }

    suspend fun get(groupId: UUID, expenseId: UUID, callerId: UUID): Expense {
        groupService.requireMembership(groupId, callerId)
        val expense = expenses.findById(expenseId)?.takeIf { it.groupId.value == groupId.toString() }
            ?: throw AppException.NotFound("Expense")
        return expense
    }

    suspend fun list(groupId: UUID, callerId: UUID, cursor: Cursor?, limit: Int): List<Expense> {
        groupService.requireMembership(groupId, callerId)
        return expenses.listForGroup(groupId, cursor, limit)
    }

    suspend fun update(
        groupId: UUID,
        expenseId: UUID,
        request: UpdateExpenseRequest,
        callerId: UUID,
    ): Expense {
        val (group, role) = groupService.requireMembership(groupId, callerId)
        val existing = get(groupId, expenseId, callerId)
        requireCanMutate(existing, callerId, role)

        val total = request.total ?: existing.total
        requireGroupCurrency(group, total)

        val requestedSplit = request.split
        val shares = when {
            requestedSplit != null -> {
                val input = requestedSplit.toSplitInput().withDefaultParticipants(group)
                requireParticipantsAreMembers(group, input)
                computeShares(total, input)
            }
            // A changed total with an unchanged EQUAL split still has to be re-divided.
            request.total != null -> computeShares(
                total,
                SplitInput.Equal(existing.shares.map { it.userId }),
            )
            else -> null
        }
        request.paidBy?.let { requirePayerIsMember(group, it) }

        return expenses.update(
            id = expenseId,
            description = request.description,
            total = request.total,
            category = request.category,
            paidBy = request.paidBy?.let { UUID.fromString(it.value) },
            splitMethod = requestedSplit?.method,
            shares = shares,
            receiptUrl = request.receiptUrl,
            notes = request.notes,
            incurredAt = request.incurredAt,
        ) ?: throw AppException.NotFound("Expense")
    }

    suspend fun delete(groupId: UUID, expenseId: UUID, callerId: UUID) {
        val (_, role) = groupService.requireMembership(groupId, callerId)
        val existing = get(groupId, expenseId, callerId)
        requireCanMutate(existing, callerId, role)
        expenses.softDelete(expenseId)
    }

    // --- rules ---------------------------------------------------------------

    /**
     * Recomputes the split server-side. The client sends inputs, never amounts, so
     * a tampered or stale client cannot persist shares that do not add up.
     */
    private fun computeShares(total: Money, input: SplitInput): List<ExpenseShare> =
        when (val outcome = SplitCalculator.calculate(total, input)) {
            is SplitOutcome.Success -> outcome.shares
            is SplitOutcome.Failure -> throw outcome.error.toAppException()
        }

    /** An EQUAL split with no explicit participants means "everyone in the group". */
    private fun SplitInput.withDefaultParticipants(group: Group): SplitInput =
        if (this is SplitInput.Equal && participants.isEmpty()) {
            SplitInput.Equal(group.members.map { it.userId })
        } else {
            this
        }

    private fun requireGroupCurrency(group: Group, amount: Money) {
        if (amount.currency != group.currency) {
            throw AppException.BadRequest(
                "This group settles in ${group.currency.code}, but the amount is in ${amount.currency.code}",
                ApiErrorCode.CURRENCY_MISMATCH,
            )
        }
    }

    private fun requireParticipantsAreMembers(group: Group, input: SplitInput) {
        val memberIds = group.members.map { it.userId }.toSet()
        val stranger = input.participants.firstOrNull { it !in memberIds }
        if (stranger != null) {
            throw AppException.Validation(
                listOf(FieldError("split", "${stranger.value} is not a member of this group"))
            )
        }
    }

    private fun requirePayerIsMember(group: Group, payer: UserId) {
        if (group.members.none { it.userId == payer }) {
            throw AppException.Validation(
                listOf(FieldError("paidBy", "The payer must be a member of this group"))
            )
        }
    }

    /** Authors can edit their own entries; owners and admins can edit anyone's. */
    private fun requireCanMutate(expense: Expense, callerId: UUID, role: GroupRole) {
        val isAuthor = expense.createdBy.value == callerId.toString()
        if (!isAuthor && role !in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
            throw AppException.Forbidden("Only the author or a group admin can change this expense")
        }
    }
}

private fun SplitError.toAppException(): AppException = when (this) {
    SplitError.NoParticipants -> AppException.Validation(
        listOf(FieldError("split.participantIds", "Select at least one participant"))
    )
    SplitError.NonPositiveTotal -> AppException.Validation(
        listOf(FieldError("total", "Amount must be greater than zero"))
    )
    is SplitError.DuplicateParticipant -> AppException.Validation(
        listOf(FieldError("split", "${userId.value} appears more than once"))
    )
    is SplitError.ExactAmountsDoNotSumToTotal -> AppException.BadRequest(
        "Shares add up to $actual but the total is $expected",
        ApiErrorCode.SPLIT_INVALID,
    )
    is SplitError.PercentagesDoNotSumTo100 -> AppException.BadRequest(
        "Percentages add up to ${actualBasisPoints / 100.0}%, they must total 100%",
        ApiErrorCode.SPLIT_INVALID,
    )
    is SplitError.NonPositiveShare -> AppException.Validation(
        listOf(FieldError("split.shares", "${userId.value} must have at least one share"))
    )
    is SplitError.NegativeAmount -> AppException.Validation(
        listOf(FieldError("split", "${userId.value} cannot have a negative share"))
    )
}
