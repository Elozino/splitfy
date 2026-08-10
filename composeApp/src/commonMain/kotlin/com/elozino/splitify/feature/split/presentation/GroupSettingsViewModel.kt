package com.elozino.splitify.feature.split.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.core.domain.DataResult
import com.elozino.splitify.core.domain.repository.GroupRepository
import com.elozino.splitify.core.domain.session.SessionManager
import com.elozino.splitify.core.domain.userMessage
import com.elozino.splitify.shared.api.dto.UpdateGroupRequest
import com.elozino.splitify.shared.domain.model.Group
import com.elozino.splitify.shared.domain.model.GroupCategory
import com.elozino.splitify.shared.domain.model.UserId
import com.elozino.splitify.shared.validation.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupSettingsUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val name: String = "",
    val description: String = "",
    val category: GroupCategory = GroupCategory.OTHER,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    /** Set once the user has left or archived, so the screen can pop. */
    val closed: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val currentUserId: String? = null,
) {
    val groupName: String get() = group?.name ?: "Group"

    /** Only owners and admins see the destructive actions. */
    val canManage: Boolean
        get() = group?.canManage(UserId(currentUserId.orEmpty())) ?: false
}

class GroupSettingsViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupSettingsUiState())
    val state: StateFlow<GroupSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = groupRepository.get(groupId)) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        group = result.data,
                        name = result.data.name,
                        description = result.data.description.orEmpty(),
                        category = result.data.category,
                        currentUserId = sessionManager.currentUserId(),
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun onNameChange(value: String) =
        _state.update { it.copy(name = value, fieldErrors = it.fieldErrors - "name", saved = false) }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value, saved = false) }

    fun onCategoryChange(category: GroupCategory) = _state.update { it.copy(category = category, saved = false) }

    fun save() {
        val current = _state.value
        Validators.displayName(current.name, "name")?.let { error ->
            _state.update { it.copy(fieldErrors = mapOf(error.field to error.message)) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val result = groupRepository.update(
                groupId,
                UpdateGroupRequest(
                    name = current.name.trim(),
                    description = current.description.takeIf { it.isNotBlank() },
                    category = current.category,
                ),
            )
            when (result) {
                is DataResult.Success -> _state.update {
                    it.copy(isSaving = false, saved = true, group = result.data)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /**
     * Leaving is a member removal, which the backend refuses while the member still
     * has a non-zero balance — the error surfaces here rather than being swallowed.
     */
    fun leaveGroup() {
        val userId = sessionManager.currentUserId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            when (val result = groupRepository.removeMember(groupId, userId)) {
                is DataResult.Success -> _state.update { it.copy(isSaving = false, closed = true) }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /** Archive, not delete: the ledger stays intact and the group can be restored. */
    fun archiveGroup() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            when (val result = groupRepository.setArchived(groupId, archived = true)) {
                is DataResult.Success -> _state.update { it.copy(isSaving = false, closed = true) }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
}
