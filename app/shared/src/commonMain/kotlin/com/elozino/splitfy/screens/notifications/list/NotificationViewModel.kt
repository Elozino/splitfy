package com.elozino.splitfy.screens.notifications.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.NotificationsResponse
import com.elozino.splitfy.repositories.NotificationRepository
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationState())
    val state: StateFlow<NotificationState> = _state.asStateFlow()

    init { load() }

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

    private fun apply(result: DataResult<NotificationsResponse>) {
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

