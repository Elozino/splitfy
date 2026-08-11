package com.elozino.splitfy.domain

import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    private val ngn = CurrencyCode.NGN
    private val jpy = CurrencyCode("JPY")

    @Test
    fun `even split of an indivisible amount preserves the total`() {
        val parts = Money(1000, ngn).splitEvenly(3)
        assertEquals(listOf(334L, 333L, 333L), parts.map { it.amount })
        assertEquals(1000L, parts.sumOf { it.amount })
    }

    @Test
    fun `weighted allocation preserves the total`() {
        val parts = Money(10_000, ngn).allocate(listOf(1, 1, 1, 1, 1, 1, 1))
        assertEquals(10_000L, parts.sumOf { it.amount })
    }

    @Test
    fun `allocation gives leftovers to the largest fractional shares`() {
        // 100 across weights 1:1:1 -> 33.33 each, remainder 1 goes to the first.
        val parts = Money(100, ngn).allocate(listOf(1, 1, 1))
        assertEquals(listOf(34L, 33L, 33L), parts.map { it.amount })
    }

    @Test
    fun `allocation of a negative amount preserves sign and total`() {
        val parts = Money(-1000, ngn).allocate(listOf(1, 1, 1))
        assertEquals(-1000L, parts.sumOf { it.amount })
        assertEquals(listOf(-334L, -333L, -333L), parts.map { it.amount })
    }

    @Test
    fun `uneven weights allocate proportionally`() {
        val parts = Money(1000, ngn).allocate(listOf(2, 1, 1))
        assertEquals(listOf(500L, 250L, 250L), parts.map { it.amount })
    }

    @Test
    fun `plain string honours currency minor units`() {
        assertEquals("12.34", Money(1234, ngn).toPlainString())
        assertEquals("-12.34", Money(-1234, ngn).toPlainString())
        assertEquals("0.05", Money(5, ngn).toPlainString())
        assertEquals("1234", Money(1234, jpy).toPlainString())
    }

    @Test
    fun `parse round-trips through plain string`() {
        assertEquals(Money(1234, ngn), Money.parse("12.34", ngn))
        assertEquals(Money(-1234, ngn), Money.parse("-12.34", ngn))
        assertEquals(Money(1200, ngn), Money.parse("12", ngn))
        assertEquals(Money(120_000, ngn), Money.parse("1,200", ngn))
    }

    @Test
    fun `parse rejects more precision than the currency allows`() {
        assertNull(Money.parse("12.345", ngn))
        assertNull(Money.parse("abc", ngn))
        assertNull(Money.parse("1.2.3", ngn))
    }
}
