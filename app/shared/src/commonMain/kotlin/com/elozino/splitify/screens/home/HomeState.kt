package com.elozino.splitify.screens

import com.elozino.splitify.api.dto.GroupSummary
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.LedgerEntry
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.User

data class HomeState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val groups: List<GroupSummary> = emptyList(),
    val walletBalance: Money = Money.zero(CurrencyCode.NGN),
    val recentEntries: List<LedgerEntry> = emptyList(),
    val unreadNotifications: Int = 0,
    val currency: CurrencyCode = CurrencyCode.NGN,
    val errorMessage: String? = null,
) {
    val hasActivity: Boolean get() = recentEntries.isNotEmpty()
    val owedToYouByCurrency: Map<CurrencyCode, Money> get() = sumByCurrency { it.isPositive }
    val youOweByCurrency: Map<CurrencyCode, Money>
        get() = sumByCurrency { it.isNegative }.mapValues { it.value.abs() }
    val totalOwedToYou: Money get() = owedToYouByCurrency[currency] ?: Money.zero(currency)
    val totalYouOwe: Money get() = youOweByCurrency[currency] ?: Money.zero(currency)
    val netPosition: Money get() = totalOwedToYou - totalYouOwe
    val hasOtherCurrencies: Boolean
        get() = (owedToYouByCurrency.keys + youOweByCurrency.keys).any { it != currency }

    private fun sumByCurrency(predicate: (Money) -> Boolean): Map<CurrencyCode, Money> =
        groups.map { it.yourBalance }
            .filter(predicate)
            .groupBy { it.currency }
            .mapValues { (code, amounts) ->
                amounts.fold(Money.zero(code)) { acc, amount -> acc + amount }
            }
}
