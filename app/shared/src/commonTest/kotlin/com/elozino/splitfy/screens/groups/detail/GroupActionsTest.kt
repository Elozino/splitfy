package com.elozino.splitfy.screens.groups.detail

import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.ExpenseId
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.SuggestedTransfer
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.screens.groups.detail.shared.GroupActions
import com.elozino.splitfy.screens.groups.detail.shared.GroupDetailState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Which bottom actions a group offers, from where its money stands. */
class GroupActionsTest {

    private val epoch = Instant.fromEpochSeconds(0)
    private val ada = UserId("ada")
    private val bayo = UserId("bayo")
    private val group = Group(GroupId("g"), "Lagos trip", currency = CurrencyCode.NGN, createdBy = ada, createdAt = epoch)
    private val dinner = Expense(
        id = ExpenseId("e1"),
        groupId = group.id,
        description = "Dinner",
        total = Money(10_000, CurrencyCode.NGN),
        paidBy = ada,
        splitMethod = SplitMethod.EQUAL,
        shares = emptyList(),
        incurredAt = epoch,
        createdBy = ada,
        createdAt = epoch,
        updatedAt = epoch,
    )
    private val bayoOwesAda = SuggestedTransfer(bayo, ada, Money(5_000, CurrencyCode.NGN))

    @Test
    fun `nothing is offered before the group has loaded`() {
        assertEquals(GroupActions.None, GroupDetailState(group = null).actions)
    }

    @Test
    fun `a group with no expenses can only add one`() {
        assertEquals(GroupActions.AddOnly, GroupDetailState(group = group).actions)
    }

    @Test
    fun `a group where someone owes offers both`() {
        val state = GroupDetailState(group = group, expenses = listOf(dinner), suggestedTransfers = listOf(bayoOwesAda))
        assertEquals(GroupActions.AddAndSettle, state.actions)
    }

    @Test
    fun `a group where nobody owes is settled up`() {
        val state = GroupDetailState(group = group, expenses = listOf(dinner), suggestedTransfers = emptyList())
        assertEquals(GroupActions.SettledUp, state.actions)
    }
}
