package com.elozino.splitfy.screens.activity.list

import com.elozino.splitfy.api.dto.ActivityFilter
import com.elozino.splitfy.api.dto.DebtEntry
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.UserId

data class ActivityState(
    val isLoading: Boolean = true,
    val filter: ActivityFilter = ActivityFilter.ALL,
    val entries: List<DebtEntry> = emptyList(),
    val totalYouOwe: Money = Money.zero(CurrencyCode.NGN),
    val totalOwedToYou: Money = Money.zero(CurrencyCode.NGN),
    val currentUserId: String? = null,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
    fun nameFor(userId: UserId, fallback: String): String =
        if (userId.value == currentUserId) "$fallback (You)" else fallback
    fun isPayer(entry: DebtEntry): Boolean = entry.fromUserId.value == currentUserId
}

