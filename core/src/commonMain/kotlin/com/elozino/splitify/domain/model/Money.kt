package com.elozino.splitify.domain.model

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlinx.serialization.Serializable

/**
 * An ISO-4217 currency code plus the number of digits after the decimal separator.
 *
 * Only the code travels over the wire; [minorUnitDigits] is derived locally so the
 * client and the server always agree on how to render a [Money] value.
 */
@Serializable
@JvmInline
value class CurrencyCode(val code: String) {

    val minorUnitDigits: Int
        get() = when (code) {
            "JPY", "KRW", "VND", "CLP", "ISK", "XAF", "XOF" -> 0
            "BHD", "IQD", "JOD", "KWD", "OMR", "TND" -> 3
            else -> 2
        }

    override fun toString(): String = code

    companion object {
        val NGN = CurrencyCode("NGN")
        val USD = CurrencyCode("USD")
        val EUR = CurrencyCode("EUR")
        val GBP = CurrencyCode("GBP")

        fun isValid(code: String): Boolean =
            code.length == 3 && code.all { it in 'A'..'Z' }
    }
}

/**
 * A monetary amount held as an integer number of **minor units** (kobo, cents…).
 *
 * Floating point is never used for money: `0.1 + 0.2 != 0.3` would silently corrupt
 * balances that are summed thousands of times over a group's lifetime.
 */
@Serializable
data class Money(
    val amount: Long,
    val currency: CurrencyCode,
) : Comparable<Money> {

    val isZero: Boolean get() = amount == 0L
    val isPositive: Boolean get() = amount > 0L
    val isNegative: Boolean get() = amount < 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amount = amount + other.amount)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amount = amount - other.amount)
    }

    operator fun times(factor: Long): Money = copy(amount = amount * factor)

    operator fun unaryMinus(): Money = copy(amount = -amount)

    fun abs(): Money = copy(amount = abs(amount))

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    /**
     * Splits this amount across [weights], guaranteeing that the parts sum back to
     * exactly this amount.
     *
     * Uses the largest-remainder method: every party gets the floor of its exact
     * share, and the leftover minor units go to the parties with the largest
     * discarded fraction (ties broken by position, so the result is deterministic
     * and identical on the client and the server).
     */
    fun allocate(weights: List<Long>): List<Money> {
        require(weights.isNotEmpty()) { "Cannot allocate across zero parties" }
        require(weights.all { it >= 0 }) { "Allocation weights must be non-negative" }
        val totalWeight = weights.sum()
        require(totalWeight > 0) { "Allocation weights must not all be zero" }

        val sign = if (amount < 0) -1L else 1L
        val absolute = abs(amount)

        val floors = weights.map { absolute / totalWeight * it + absolute % totalWeight * it / totalWeight }
        var remainder = absolute - floors.sum()

        // Rank by discarded fraction, descending; index breaks ties deterministically.
        val ranking = weights
            .mapIndexed { index, weight -> index to (absolute % totalWeight * weight) % totalWeight }
            .sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first })

        val parts = floors.toMutableList()
        var cursor = 0
        while (remainder > 0 && cursor < ranking.size) {
            parts[ranking[cursor].first] += 1
            remainder -= 1
            cursor += 1
        }
        return parts.map { Money(sign * it, currency) }
    }

    /** Convenience over [allocate] for an even N-way split. */
    fun splitEvenly(parts: Int): List<Money> {
        require(parts > 0) { "Cannot split into $parts parts" }
        return allocate(List(parts) { 1L })
    }

    /** `1234` NGN -> `"12.34"`. Locale-independent; formatting for display only. */
    fun toPlainString(): String {
        val digits = currency.minorUnitDigits
        if (digits == 0) return amount.toString()
        val sign = if (amount < 0) "-" else ""
        val absolute = abs(amount).toString().padStart(digits + 1, '0')
        val whole = absolute.dropLast(digits)
        val fraction = absolute.takeLast(digits)
        return "$sign$whole.$fraction"
    }

    private fun requireSameCurrency(other: Money) =
        require(currency == other.currency) {
            "Currency mismatch: ${currency.code} vs ${other.currency.code}"
        }

    companion object {
        fun zero(currency: CurrencyCode) = Money(0, currency)

        /** Parses `"12.34"` into minor units. Returns `null` on malformed input. */
        fun parse(value: String, currency: CurrencyCode): Money? {
            val trimmed = value.trim().replace(",", "")
            if (trimmed.isEmpty()) return null
            val negative = trimmed.startsWith("-")
            val unsigned = trimmed.removePrefix("-").removePrefix("+")
            val parts = unsigned.split('.')
            if (parts.size > 2) return null
            val whole = parts[0].ifEmpty { "0" }
            val fraction = parts.getOrElse(1) { "" }
            if (!whole.all(Char::isDigit) || !fraction.all(Char::isDigit)) return null
            val digits = currency.minorUnitDigits
            if (fraction.length > digits) return null
            val normalized = whole + fraction.padEnd(digits, '0')
            val minor = normalized.toLongOrNull() ?: return null
            return Money(if (negative) -minor else minor, currency)
        }
    }
}

/** Sums a collection of same-currency amounts, or returns zero for an empty list. */
fun Iterable<Money>.sumOrZero(currency: CurrencyCode): Money =
    fold(Money.zero(currency)) { acc, money -> acc + money }
