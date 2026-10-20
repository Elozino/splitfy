package com.elozino.splitfy.screens.home.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.FeatureFlags
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.LedgerEntry
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.repositories.NotificationRepository
import com.elozino.splitfy.repositories.UserRepository
import com.elozino.splitfy.repositories.WalletRepository
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.userMessage
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
            val wallet = async { if (FeatureFlags.WALLET) walletRepository.wallet() else null }
            val ledger = async { if (FeatureFlags.WALLET) walletRepository.ledger() else null }
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

