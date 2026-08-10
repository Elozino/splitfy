package com.elozino.splitify.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.api.dto.GroupSummary
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.LedgerEntry
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.User
import com.elozino.splitify.repositories.GroupRepository
import com.elozino.splitify.repositories.NotificationRepository
import com.elozino.splitify.repositories.UserRepository
import com.elozino.splitify.repositories.WalletRepository
import com.elozino.splitify.SessionManager
import com.elozino.splitify.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
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

    /**
     * Totals are per currency: groups may settle in different currencies, and adding
     * ₦ to $ would be nonsense. The headline figures below cover the user's default
     * currency; the maps expose the rest for a breakdown row.
     */
    val owedToYouByCurrency: Map<CurrencyCode, Money>
        get() = sumByCurrency { it.isPositive }

    val youOweByCurrency: Map<CurrencyCode, Money>
        get() = sumByCurrency { it.isNegative }.mapValues { it.value.abs() }

    val totalOwedToYou: Money
        get() = owedToYouByCurrency[currency] ?: Money.zero(currency)

    val totalYouOwe: Money
        get() = youOweByCurrency[currency] ?: Money.zero(currency)

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

class HomeViewModel(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val walletRepository: WalletRepository,
    private val notificationRepository: NotificationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            // Five independent reads: fetching them in parallel means the dashboard
            // renders in one round trip's time rather than five.
            val profile = async { userRepository.me() }
            val groups = async { groupRepository.list() }
            val wallet = async { walletRepository.wallet() }
            val ledger = async { walletRepository.ledger() }
            val notifications = async { notificationRepository.list() }

            val profileResult = profile.await()
            val groupsResult = groups.await()
            val walletResult = wallet.await()
            val ledgerResult = ledger.await()
            val notificationsResult = notifications.await()

            (profileResult as? DataResult.Success)?.data?.let(sessionManager::onUserLoaded)

            _state.update { current ->
                val user = (profileResult as? DataResult.Success)?.data
                current.copy(
                    isLoading = false,
                    user = user ?: current.user,
                    currency = user?.defaultCurrency ?: current.currency,
                    groups = (groupsResult as? DataResult.Success)?.data ?: current.groups,
                    walletBalance = (walletResult as? DataResult.Success)?.data?.balance
                        ?: current.walletBalance,
                    recentEntries = (ledgerResult as? DataResult.Success)?.data?.items?.take(RECENT_LIMIT)
                        ?: current.recentEntries,
                    unreadNotifications = (notificationsResult as? DataResult.Success)?.data?.unreadCount
                        ?: current.unreadNotifications,
                    errorMessage = listOfNotNull(
                        (profileResult as? DataResult.Failure)?.error,
                        (groupsResult as? DataResult.Failure)?.error,
                    ).firstOrNull()?.userMessage(),
                )
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
