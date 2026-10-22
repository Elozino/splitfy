package com.elozino.splitfy.screens.expenses

import com.elozino.splitfy.domain.model.ExpenseCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpenseCategoryUiTest {

    @Test
    fun `every category has its own emoji`() {
        val emojis = ExpenseCategory.entries.map { it.emoji() }
        assertEquals(emojis.size, emojis.toSet().size)
        assertEquals("🚕", ExpenseCategory.TRANSPORT.emoji())
        assertEquals("🏨", ExpenseCategory.ACCOMMODATION.emoji())
    }

    @Test
    fun `labels read as sentence case`() {
        assertEquals("Groceries", ExpenseCategory.GROCERIES.label())
        assertEquals("Accommodation", ExpenseCategory.ACCOMMODATION.label())
    }
}
