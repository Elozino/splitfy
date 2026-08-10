package com.elozino.splitify.domain

import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.Expense
import com.elozino.splitify.domain.model.ExpenseId
import com.elozino.splitify.domain.model.ExpenseShare
import com.elozino.splitify.domain.model.GroupId
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.Settlement
import com.elozino.splitify.domain.model.SettlementId
import com.elozino.splitify.domain.model.SettlementStatus
import com.elozino.splitify.domain.model.SplitMethod
import com.elozino.splitify.domain.model.UserId
import com.elozino.splitify.domain.split.BalanceCalculator
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalanceCalculatorTest {

    private val ngn = CurrencyCode.NGN
    private val group = GroupId("g1")
    private val epoch = Instant.fromEpochSeconds(0)
    private val ada = UserId("ada")
    private val bode = UserId("bode")
    private val chidi = UserId("chidi")

    private fun expense(
        id: String,
        paidBy: UserId,
        total: Long,
        shares: Map<UserId, Long>,
        deleted: Boolean = false,
    ) = Expense(
        id = ExpenseId(id),
        groupId = group,
        description = id,
        total = Money(total, ngn),
        paidBy = paidBy,
        splitMethod = SplitMethod.EXACT,
        shares = shares.map { ExpenseShare(it.key, Money(it.value, ngn)) },
        incurredAt = epoch,
        createdBy = paidBy,
        createdAt = epoch,
        updatedAt = epoch,
        deletedAt = if (deleted) epoch else null,
    )

    @Test
    fun `balances always sum to zero`() {
        val balances = BalanceCalculator.balances(
            currency = ngn,
            expenses = listOf(
                expense("e1", ada, 9000, mapOf(ada to 3000, bode to 3000, chidi to 3000)),
                expense("e2", bode, 6000, mapOf(ada to 2000, bode to 2000, chidi to 2000)),
            ),
        )
        assertEquals(0L, balances.sumOf { it.net.amount })
    }

    @Test
    fun `the payer is owed what everyone else consumed`() {
        val balances = BalanceCalculator.balances(
            currency = ngn,
            expenses = listOf(expense("e1", ada, 9000, mapOf(ada to 3000, bode to 3000, chidi to 3000))),
        )
        assertEquals(6000L, balances.first { it.userId == ada }.net.amount)
        assertEquals(-3000L, balances.first { it.userId == bode }.net.amount)
    }

    @Test
    fun `deleted expenses are excluded from the ledger`() {
        val balances = BalanceCalculator.balances(
            currency = ngn,
            expenses = listOf(
                expense("e1", ada, 9000, mapOf(ada to 3000, bode to 3000, chidi to 3000), deleted = true),
            ),
        )
        assertTrue(balances.all { it.net.isZero })
    }

    @Test
    fun `only confirmed settlements move the needle`() {
        val expenses = listOf(expense("e1", ada, 9000, mapOf(ada to 3000, bode to 3000, chidi to 3000)))
        val pending = Settlement(
            id = SettlementId("s1"), groupId = group, fromUserId = bode, toUserId = ada,
            amount = Money(3000, ngn), status = SettlementStatus.PENDING,
            settledAt = epoch, createdBy = bode, createdAt = epoch,
        )

        val beforeConfirm = BalanceCalculator.balances(ngn, expenses, listOf(pending))
        assertEquals(-3000L, beforeConfirm.first { it.userId == bode }.net.amount)

        val afterConfirm = BalanceCalculator.balances(
            ngn, expenses, listOf(pending.copy(status = SettlementStatus.CONFIRMED)),
        )
        assertEquals(0L, afterConfirm.first { it.userId == bode }.net.amount)
        assertEquals(3000L, afterConfirm.first { it.userId == ada }.net.amount)
    }

    @Test
    fun `simplify clears every balance in at most n-1 transfers`() {
        val balances = BalanceCalculator.balances(
            currency = ngn,
            expenses = listOf(
                expense("e1", ada, 9000, mapOf(ada to 3000, bode to 3000, chidi to 3000)),
                expense("e2", bode, 3000, mapOf(ada to 1000, bode to 1000, chidi to 1000)),
            ),
        )
        val transfers = BalanceCalculator.simplify(balances)

        assertTrue(transfers.size <= balances.size - 1, "expected at most n-1 transfers")

        // Applying the transfers must zero out every position.
        val settled = balances.associate { it.userId to it.net.amount }.toMutableMap()
        transfers.forEach { transfer ->
            settled[transfer.from] = (settled[transfer.from] ?: 0L) + transfer.amount.amount
            settled[transfer.to] = (settled[transfer.to] ?: 0L) - transfer.amount.amount
        }
        assertTrue(settled.values.all { it == 0L }, "unsettled balances: $settled")
    }

    @Test
    fun `simplify returns nothing when everyone is square`() {
        val balances = BalanceCalculator.balances(
            currency = ngn,
            expenses = listOf(expense("e1", ada, 3000, mapOf(ada to 3000))),
        )
        assertEquals(emptyList(), BalanceCalculator.simplify(balances))
    }
}
