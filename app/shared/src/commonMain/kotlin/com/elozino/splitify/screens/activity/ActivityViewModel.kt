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

class ActivityViewModel(
    private val activityRepository: ActivityRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityState(currentUserId = sessionManager.currentUserId()))
    val state: StateFlow<ActivityState> = _state.asStateFlow()

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
