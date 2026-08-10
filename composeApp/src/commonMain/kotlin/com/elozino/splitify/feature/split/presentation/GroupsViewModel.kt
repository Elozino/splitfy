package com.elozino.splitify.feature.split.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.core.domain.DataResult
import com.elozino.splitify.core.domain.repository.GroupRepository
import com.elozino.splitify.core.domain.userMessage
import com.elozino.splitify.shared.api.dto.CreateGroupRequest
import com.elozino.splitify.shared.api.dto.GroupSummary
import com.elozino.splitify.shared.domain.model.CurrencyCode
import com.elozino.splitify.shared.domain.model.GroupCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val isLoading: Boolean = true,
    /** True only for pull-to-refresh, so the list stays visible while reloading. */
    val isRefreshing: Boolean = false,
    val groups: List<GroupSummary> = emptyList(),
    val includeArchived: Boolean = false,
    val errorMessage: String? = null,
    val isCreating: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}

class GroupsViewModel(
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = fetch(isRefresh = false)

    fun refresh() = fetch(isRefresh = true)

    fun toggleArchived() {
        _state.update { it.copy(includeArchived = !it.includeArchived) }
        load()
    }

    fun createGroup(name: String, currency: CurrencyCode, category: GroupCategory) {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, errorMessage = null) }
            val result = groupRepository.create(
                CreateGroupRequest(name = name, currency = currency, category = category)
            )
            when (result) {
                is DataResult.Success -> {
                    _state.update { it.copy(isCreating = false) }
                    load()
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isCreating = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    private fun fetch(isRefresh: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(isLoading = !isRefresh && it.groups.isEmpty(), isRefreshing = isRefresh)
            }
            when (val result = groupRepository.list(_state.value.includeArchived)) {
                is DataResult.Success -> _state.update {
                    it.copy(isLoading = false, isRefreshing = false, groups = result.data)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.error.userMessage(),
                    )
                }
            }
        }
    }
}
