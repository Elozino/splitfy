package com.elozino.splitify.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.api.dto.ActivityFilter
import com.elozino.splitify.api.dto.AppNotification
import com.elozino.splitify.api.dto.DebtEntry
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.UserId
import com.elozino.splitify.repositories.ActivityRepository
import com.elozino.splitify.repositories.NotificationRepository
import com.elozino.splitify.SessionManager
import com.elozino.splitify.userMessage
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityUiState(
    val isLoading: Boolean = true,
    val filter: ActivityFilter = ActivityFilter.ALL,
    val entries: List<DebtEntry> = emptyList(),
    val totalYouOwe: Money = Money.zero(CurrencyCode.NGN),
    val totalOwedToYou: Money = Money.zero(CurrencyCode.NGN),
    val currentUserId: String? = null,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()

    /** "You" reads better than the user's own name in a debt line. */
    fun nameFor(userId: UserId, fallback: String): String =
        if (userId.value == currentUserId) "$fallback (You)" else fallback

    /** Whether the signed-in user is the one who has to pay. */
    fun isPayer(entry: DebtEntry): Boolean = entry.fromUserId.value == currentUserId
}

class ActivityViewModel(
    private val activityRepository: ActivityRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState(currentUserId = sessionManager.currentUserId()))
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onFilterChange(filter: ActivityFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = activityRepository.activity(_state.value.filter)) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        entries = result.data.entries,
                        totalYouOwe = result.data.totalYouOwe,
                        totalOwedToYou = result.data.totalOwedToYou,
                        currentUserId = sessionManager.currentUserId(),
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
}

data class NotificationUiState(
    val isLoading: Boolean = true,
    val items: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

class NotificationViewModel(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            apply(notificationRepository.list())
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch { apply(notificationRepository.markRead(id)) }
    }

    fun markAllRead() {
        viewModelScope.launch { apply(notificationRepository.markAllRead()) }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    private fun apply(result: DataResult<com.elozino.splitify.api.dto.NotificationsResponse>) {
        _state.update { current ->
            when (result) {
                is DataResult.Success -> current.copy(
                    isLoading = false,
                    items = result.data.items,
                    unreadCount = result.data.unreadCount,
                )
                is DataResult.Failure -> current.copy(
                    isLoading = false,
                    errorMessage = result.error.userMessage(),
                )
            }
        }
    }
}
