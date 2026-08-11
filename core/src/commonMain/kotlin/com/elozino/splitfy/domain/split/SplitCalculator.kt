package com.elozino.splitfy.domain.split

import com.elozino.splitfy.domain.model.ExpenseShare
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.UserId

/** The user-supplied side of a split, before any rounding is applied. */
sealed interface SplitInput {

    val participants: List<UserId>

    data class Equal(override val participants: List<UserId>) : SplitInput

    /** Exact minor units per participant. Must sum to the expense total. */
    data class Exact(val amounts: Map<UserId, Long>) : SplitInput {
        override val participants: List<UserId> get() = amounts.keys.toList()
    }

    /** Basis points per participant (1% = 100). Must sum to 10 000. */
    data class Percentage(val basisPoints: Map<UserId, Int>) : SplitInput {
        override val participants: List<UserId> get() = basisPoints.keys.toList()
    }

    /** Relative share counts per participant. Must be positive. */
    data class Shares(val shares: Map<UserId, Int>) : SplitInput {
        override val participants: List<UserId> get() = shares.keys.toList()
    }

    val method: SplitMethod
        get() = when (this) {
            is Equal -> SplitMethod.EQUAL
            is Exact -> SplitMethod.EXACT
            is Percentage -> SplitMethod.PERCENTAGE
            is Shares -> SplitMethod.SHARES
        }
}

sealed interface SplitError {
    data object NoParticipants : SplitError
    data object NonPositiveTotal : SplitError
    data class DuplicateParticipant(val userId: UserId) : SplitError
    data class ExactAmountsDoNotSumToTotal(val expected: Long, val actual: Long) : SplitError
    data class PercentagesDoNotSumTo100(val actualBasisPoints: Int) : SplitError
    data class NonPositiveShare(val userId: UserId) : SplitError
    data class NegativeAmount(val userId: UserId) : SplitError
}

sealed interface SplitOutcome {
    data class Success(val shares: List<ExpenseShare>) : SplitOutcome
    data class Failure(val error: SplitError) : SplitOutcome
}

/**
 * Turns an expense total plus a [SplitInput] into per-participant amounts.
 *
 * This lives in `:shared` on purpose: the client previews a split offline and the
 * server re-computes it on write. Both run byte-identical code, so a device can
 * never persist a split the backend would have rounded differently.
 */
object SplitCalculator {

    fun calculate(total: Money, input: SplitInput): SplitOutcome {
        if (input.participants.isEmpty()) return SplitOutcome.Failure(SplitError.NoParticipants)
        if (!total.isPositive) return SplitOutcome.Failure(SplitError.NonPositiveTotal)

        input.participants
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.let { return SplitOutcome.Failure(SplitError.DuplicateParticipant(it.key)) }

        return when (input) {
            is SplitInput.Equal -> equalSplit(total, input)
            is SplitInput.Exact -> exactSplit(total, input)
            is SplitInput.Percentage -> percentageSplit(total, input)
            is SplitInput.Shares -> shareSplit(total, input)
        }
    }

    private fun equalSplit(total: Money, input: SplitInput.Equal): SplitOutcome {
        val parts = total.splitEvenly(input.participants.size)
        return SplitOutcome.Success(
            input.participants.mapIndexed { index, userId ->
                ExpenseShare(userId = userId, amount = parts[index], weight = 1)
            }
        )
    }

    private fun exactSplit(total: Money, input: SplitInput.Exact): SplitOutcome {
        input.amounts.entries.firstOrNull { it.value < 0 }
            ?.let { return SplitOutcome.Failure(SplitError.NegativeAmount(it.key)) }

        val sum = input.amounts.values.sum()
        if (sum != total.amount) {
            return SplitOutcome.Failure(SplitError.ExactAmountsDoNotSumToTotal(total.amount, sum))
        }
        return SplitOutcome.Success(
            input.amounts.map { (userId, minorUnits) ->
                ExpenseShare(userId, Money(minorUnits, total.currency), minorUnits)
            }
        )
    }

    private fun percentageSplit(total: Money, input: SplitInput.Percentage): SplitOutcome {
        input.basisPoints.entries.firstOrNull { it.value < 0 }
            ?.let { return SplitOutcome.Failure(SplitError.NegativeAmount(it.key)) }

        val sum = input.basisPoints.values.sum()
        if (sum != ONE_HUNDRED_PERCENT_BPS) {
            return SplitOutcome.Failure(SplitError.PercentagesDoNotSumTo100(sum))
        }
        val entries = input.basisPoints.entries.toList()
        val parts = total.allocate(entries.map { it.value.toLong() })
        return SplitOutcome.Success(
            entries.mapIndexed { index, entry ->
                ExpenseShare(entry.key, parts[index], entry.value.toLong())
            }
        )
    }

    private fun shareSplit(total: Money, input: SplitInput.Shares): SplitOutcome {
        input.shares.entries.firstOrNull { it.value <= 0 }
            ?.let { return SplitOutcome.Failure(SplitError.NonPositiveShare(it.key)) }

        val entries = input.shares.entries.toList()
        val parts = total.allocate(entries.map { it.value.toLong() })
        return SplitOutcome.Success(
            entries.mapIndexed { index, entry ->
                ExpenseShare(entry.key, parts[index], entry.value.toLong())
            }
        )
    }

    const val ONE_HUNDRED_PERCENT_BPS: Int = 10_000
}
