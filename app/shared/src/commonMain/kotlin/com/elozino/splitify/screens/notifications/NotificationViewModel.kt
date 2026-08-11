package com.elozino.splitify.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.DataResult
import com.elozino.splitify.api.dto.NotificationsResponse
import com.elozino.splitify.repositories.NotificationRepository
import com.elozino.splitify.userMessage
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
