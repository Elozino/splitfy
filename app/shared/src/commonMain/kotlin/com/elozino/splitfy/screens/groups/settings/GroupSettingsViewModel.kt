package com.elozino.splitfy.screens.groups.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.UpdateGroupRequest
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupCategory
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.userMessage
import com.elozino.splitfy.validation.Validators
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupSettingsViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupSettingsState())
    val state: StateFlow<GroupSettingsState> = _state.asStateFlow()

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

    fun showMenu() = _state.update { it.copy(step = GroupSettingsStep.MENU) }

    fun showEditor() = _state.update { it.copy(step = GroupSettingsStep.EDIT, saved = false) }

    fun setLeaveDialogVisible(isVisible: Boolean) =
        _state.update { it.copy(isLeaveDialogVisible = isVisible) }

    fun setDeleteDialogVisible(isVisible: Boolean) =
        _state.update { it.copy(isDeleteDialogVisible = isVisible) }

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
