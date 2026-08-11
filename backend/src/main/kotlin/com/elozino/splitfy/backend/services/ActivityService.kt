package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.dto.ActivityFilter
import com.elozino.splitfy.api.dto.ActivityResponse
import com.elozino.splitfy.api.dto.DebtEntry
import com.elozino.splitfy.backend.repositories.ExpenseRepository
import com.elozino.splitfy.backend.repositories.GroupRepository
import com.elozino.splitfy.backend.repositories.SettlementRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SettlementStatus
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.domain.split.BalanceCalculator
import java.util.UUID

/**
 * The "who owes whom" feed.
 *
 * Every line is derived from the ledger on read using the same `:shared` calculator
 * the group screen uses, so the activity list can never disagree with a group's
 * balances.
 */
class ActivityService(
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val settlements: SettlementRepository,
    private val users: UserRepository,
) {

    suspend fun activity(callerId: UUID, filter: ActivityFilter): ActivityResponse {
        val me = UserId(callerId.toString())
        val currency = users.findById(callerId)?.defaultCurrency
            ?: return ActivityResponse(emptyList(), Money.zero(DEFAULT_CURRENCY), Money.zero(DEFAULT_CURRENCY))

        val entries = groups.listForUser(callerId, includeArchived = false)
            .flatMap { group -> debtsIn(group) }
            .sortedByDescending { it.lastActivityAt }

        val visible = entries.filter { entry ->
            when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.YOU_OWE -> entry.fromUserId == me
                ActivityFilter.OWED_TO_YOU -> entry.toUserId == me
            }
        }

        // Totals always describe the whole picture, not the filtered slice.
        val youOwe = entries.filter { it.fromUserId == me && it.amount.currency == currency }
            .fold(Money.zero(currency)) { acc, entry -> acc + entry.amount }
        val owedToYou = entries.filter { it.toUserId == me && it.amount.currency == currency }
            .fold(Money.zero(currency)) { acc, entry -> acc + entry.amount }

        return ActivityResponse(visible, youOwe, owedToYou)
    }

    private suspend fun debtsIn(group: Group): List<DebtEntry> {
        val groupUuid = UUID.fromString(group.id.value)
        val ledger = expenses.allForGroup(groupUuid)
        val groupSettlements = settlements.listForGroup(groupUuid)
        if (ledger.isEmpty() && groupSettlements.isEmpty()) return emptyList()

        val names = group.members.associate { it.userId to it.displayName }
        val balances = BalanceCalculator.balances(group.currency, ledger, groupSettlements)

        val lastActivity = maxOf(
            ledger.maxOfOrNull { it.updatedAt } ?: group.createdAt,
            groupSettlements.maxOfOrNull { it.createdAt } ?: group.createdAt,
        )

        val outstanding = BalanceCalculator.simplify(balances).map { transfer ->
            DebtEntry(
                fromUserId = transfer.from,
                fromName = names[transfer.from] ?: "Someone",
                toUserId = transfer.to,
                toName = names[transfer.to] ?: "Someone",
                amount = transfer.amount,
                groupId = group.id,
                groupName = group.name,
                isSettled = false,
                lastActivityAt = lastActivity,
            )
        }

        // Confirmed settlements appear as already-cleared lines so the feed shows a
        // history rather than only what is still owed.
        val settled = groupSettlements
            .filter { it.status == SettlementStatus.CONFIRMED }
            .map { settlement ->
                DebtEntry(
                    fromUserId = settlement.fromUserId,
                    fromName = names[settlement.fromUserId] ?: "Someone",
                    toUserId = settlement.toUserId,
                    toName = names[settlement.toUserId] ?: "Someone",
                    amount = settlement.amount,
                    groupId = group.id,
                    groupName = group.name,
                    isSettled = true,
                    lastActivityAt = settlement.settledAt,
                )
            }

        return outstanding + settled
    }

    private companion object {
        val DEFAULT_CURRENCY = com.elozino.splitfy.domain.model.CurrencyCode.NGN
    }
}
