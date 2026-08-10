package com.elozino.splitify.domain.split

import com.elozino.splitify.domain.model.Balance
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.Expense
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.Settlement
import com.elozino.splitify.domain.model.SettlementStatus
import com.elozino.splitify.domain.model.SuggestedTransfer
import com.elozino.splitify.domain.model.UserId

/**
 * Derives who owes what from the expense and settlement ledger.
 *
 * Balances are never stored: they are a pure fold over the ledger, so a replayed
 * or re-synced device always converges on the same numbers as the server.
 */
object BalanceCalculator {

    /**
     * Net position per member: positive means the group owes them, negative means
     * they owe the group. The returned balances always sum to zero.
     */
    fun balances(
        currency: CurrencyCode,
        expenses: List<Expense>,
        settlements: List<Settlement> = emptyList(),
    ): List<Balance> {
        val net = mutableMapOf<UserId, Long>()

        expenses.asSequence()
            .filterNot { it.isDeleted }
            .forEach { expense ->
                net[expense.paidBy] = (net[expense.paidBy] ?: 0L) + expense.total.amount
                expense.shares.forEach { share ->
                    net[share.userId] = (net[share.userId] ?: 0L) - share.amount.amount
                }
            }

        settlements.asSequence()
            .filter { it.status == SettlementStatus.CONFIRMED }
            .forEach { settlement ->
                // Paying someone back increases the payer's net position and
                // decreases the receiver's by the same amount.
                net[settlement.fromUserId] = (net[settlement.fromUserId] ?: 0L) + settlement.amount.amount
                net[settlement.toUserId] = (net[settlement.toUserId] ?: 0L) - settlement.amount.amount
            }

        return net.map { (userId, amount) -> Balance(userId, Money(amount, currency)) }
            .sortedWith(compareByDescending<Balance> { it.net.amount }.thenBy { it.userId.value })
    }

    /**
     * Reduces the balance sheet to a minimal-ish set of payments using the classic
     * greedy "largest creditor / largest debtor" heuristic.
     *
     * Minimising transfers exactly is NP-hard; greedy needs at most `n - 1` transfers
     * and matches the optimum for the group sizes this app targets.
     */
    fun simplify(balances: List<Balance>): List<SuggestedTransfer> {
        if (balances.isEmpty()) return emptyList()
        val currency = balances.first().net.currency

        val creditors = balances.filter { it.net.isPositive }
            .map { it.userId to it.net.amount }
            .toMutableList()
        val debtors = balances.filter { it.net.isNegative }
            .map { it.userId to -it.net.amount }
            .toMutableList()

        creditors.sortWith(compareByDescending<Pair<UserId, Long>> { it.second }.thenBy { it.first.value })
        debtors.sortWith(compareByDescending<Pair<UserId, Long>> { it.second }.thenBy { it.first.value })

        val transfers = mutableListOf<SuggestedTransfer>()
        var creditorIndex = 0
        var debtorIndex = 0

        while (creditorIndex < creditors.size && debtorIndex < debtors.size) {
            val (creditor, credit) = creditors[creditorIndex]
            val (debtor, debt) = debtors[debtorIndex]
            val settled = minOf(credit, debt)

            if (settled > 0) {
                transfers += SuggestedTransfer(from = debtor, to = creditor, amount = Money(settled, currency))
            }

            creditors[creditorIndex] = creditor to (credit - settled)
            debtors[debtorIndex] = debtor to (debt - settled)

            if (creditors[creditorIndex].second == 0L) creditorIndex++
            if (debtors[debtorIndex].second == 0L) debtorIndex++
        }
        return transfers
    }

    /** What a single member should see on their group card: net owed/owing. */
    fun balanceOf(userId: UserId, balances: List<Balance>, currency: CurrencyCode): Money =
        balances.firstOrNull { it.userId == userId }?.net ?: Money.zero(currency)
}
