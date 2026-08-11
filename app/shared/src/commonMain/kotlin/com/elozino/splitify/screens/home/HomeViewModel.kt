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

class HomeViewModel(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val walletRepository: WalletRepository,
    private val notificationRepository: NotificationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

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
