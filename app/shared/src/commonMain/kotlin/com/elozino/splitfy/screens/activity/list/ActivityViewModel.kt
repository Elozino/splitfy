package com.elozino.splitfy.screens.activity.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.ActivityFilter
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.repositories.ActivityRepository
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.userMessage
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

    fun load() = fetch(isRefresh = false)

    /** Pull-to-refresh: reloads while what's on screen stays put, so no placeholders flash. */
    fun refresh() = fetch(isRefresh = true)

    private fun fetch(isRefresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !isRefresh, isRefreshing = isRefresh, errorMessage = null) }
            when (val result = activityRepository.activity(_state.value.filter)) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        entries = result.data.entries,
                        totalYouOwe = result.data.totalYouOwe,
                        totalOwedToYou = result.data.totalOwedToYou,
                        currentUserId = sessionManager.currentUserId(),
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
}

