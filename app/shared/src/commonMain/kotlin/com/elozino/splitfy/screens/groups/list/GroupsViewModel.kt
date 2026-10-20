package com.elozino.splitfy.screens.groups.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.GroupCategory
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupsViewModel(
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupsState())
    val state: StateFlow<GroupsState> = _state.asStateFlow()

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

