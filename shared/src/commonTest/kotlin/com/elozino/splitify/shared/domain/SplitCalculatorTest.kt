package com.elozino.splitify.shared.domain

import com.elozino.splitify.shared.domain.model.CurrencyCode
import com.elozino.splitify.shared.domain.model.Money
import com.elozino.splitify.shared.domain.model.UserId
import com.elozino.splitify.shared.domain.split.SplitCalculator
import com.elozino.splitify.shared.domain.split.SplitError
import com.elozino.splitify.shared.domain.split.SplitInput
import com.elozino.splitify.shared.domain.split.SplitOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SplitCalculatorTest {

    private val ngn = CurrencyCode.NGN
    private val ada = UserId("ada")
    private val bode = UserId("bode")
    private val chidi = UserId("chidi")

    private fun success(outcome: SplitOutcome) = assertIs<SplitOutcome.Success>(outcome).shares

    @Test
    fun `equal split never loses a kobo`() {
        val shares = success(
            SplitCalculator.calculate(Money(10_000, ngn), SplitInput.Equal(listOf(ada, bode, chidi)))
        )
        assertEquals(10_000L, shares.sumOf { it.amount.amount })
        assertEquals(listOf(3334L, 3333L, 3333L), shares.map { it.amount.amount })
    }

    @Test
    fun `exact split is accepted when it sums to the total`() {
        val shares = success(
            SplitCalculator.calculate(
                Money(5000, ngn),
                SplitInput.Exact(mapOf(ada to 2000L, bode to 3000L)),
            )
        )
        assertEquals(2000L, shares.first { it.userId == ada }.amount.amount)
        assertEquals(3000L, shares.first { it.userId == bode }.amount.amount)
    }

    @Test
    fun `exact split is rejected when it does not sum to the total`() {
        val outcome = SplitCalculator.calculate(
            Money(5000, ngn),
            SplitInput.Exact(mapOf(ada to 2000L, bode to 2500L)),
        )
        val failure = assertIs<SplitOutcome.Failure>(outcome)
        assertEquals(SplitError.ExactAmountsDoNotSumToTotal(5000, 4500), failure.error)
    }

    @Test
    fun `percentage split requires basis points to sum to 100 percent`() {
        val outcome = SplitCalculator.calculate(
            Money(5000, ngn),
            SplitInput.Percentage(mapOf(ada to 5000, bode to 4000)),
        )
        val failure = assertIs<SplitOutcome.Failure>(outcome)
        assertEquals(SplitError.PercentagesDoNotSumTo100(9000), failure.error)
    }

    @Test
    fun `percentage split with repeating decimals still sums to the total`() {
        // 33.33 / 33.33 / 33.34 of 100.00
        val shares = success(
            SplitCalculator.calculate(
                Money(10_000, ngn),
                SplitInput.Percentage(mapOf(ada to 3333, bode to 3333, chidi to 3334)),
            )
        )
        assertEquals(10_000L, shares.sumOf { it.amount.amount })
    }

    @Test
    fun `share split weights participants proportionally`() {
        val shares = success(
            SplitCalculator.calculate(
                Money(9000, ngn),
                SplitInput.Shares(mapOf(ada to 2, bode to 1)),
            )
        )
        assertEquals(6000L, shares.first { it.userId == ada }.amount.amount)
        assertEquals(3000L, shares.first { it.userId == bode }.amount.amount)
    }

    @Test
    fun `zero and negative totals are rejected`() {
        val outcome = SplitCalculator.calculate(Money(0, ngn), SplitInput.Equal(listOf(ada)))
        assertEquals(SplitError.NonPositiveTotal, assertIs<SplitOutcome.Failure>(outcome).error)
    }

    @Test
    fun `an empty participant list is rejected`() {
        val outcome = SplitCalculator.calculate(Money(100, ngn), SplitInput.Equal(emptyList()))
        assertEquals(SplitError.NoParticipants, assertIs<SplitOutcome.Failure>(outcome).error)
    }

    @Test
    fun `duplicate participants are rejected`() {
        val outcome = SplitCalculator.calculate(Money(100, ngn), SplitInput.Equal(listOf(ada, ada)))
        assertEquals(SplitError.DuplicateParticipant(ada), assertIs<SplitOutcome.Failure>(outcome).error)
    }

    @Test
    fun `non-positive shares are rejected`() {
        val outcome = SplitCalculator.calculate(
            Money(100, ngn),
            SplitInput.Shares(mapOf(ada to 1, bode to 0)),
        )
        assertEquals(SplitError.NonPositiveShare(bode), assertIs<SplitOutcome.Failure>(outcome).error)
    }
}
