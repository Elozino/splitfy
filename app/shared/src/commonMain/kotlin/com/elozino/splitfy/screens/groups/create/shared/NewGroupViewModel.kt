package com.elozino.splitfy.screens.groups.create.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.Contact
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.SessionState
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.GroupCategory
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.repositories.ContactRepository
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.userMessage
import com.elozino.splitfy.validation.Validators
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Carries the draft across the two-step create flow (details, then participants).
 *
 * One instance is held by the navigation graph so stepping forward and back does not
 * lose what the user typed.
 */
class NewGroupViewModel(
    private val groupRepository: GroupRepository,
    private val contactRepository: ContactRepository,
    sessionManager: SessionManager? = null,
) : ViewModel() {

    // New groups start in the currency the user picked under Personal Info.
    private val _state = MutableStateFlow(
        NewGroupState(
            currency = (sessionManager?.state?.value as? SessionState.SignedIn)?.user?.defaultCurrency ?: CurrencyCode.NGN,
        )
    )
    val state: StateFlow<NewGroupState> = _state.asStateFlow()

    init {
        loadContacts()
    }

    fun onNameChange(value: String) =
        _state.update { it.copy(name = value, fieldErrors = it.fieldErrors - "name") }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun onCategoryChange(category: GroupCategory) = _state.update { it.copy(category = category) }

    fun onCurrencyChange(currency: CurrencyCode) = _state.update { it.copy(currency = currency) }

    fun setCurrencyMenuVisible(isVisible: Boolean) =
        _state.update { it.copy(isCurrencyMenuVisible = isVisible) }

    fun onParticipantsTabSelected(index: Int) =
        _state.update { it.copy(selectedParticipantsTab = index) }

    fun onSearchChange(value: String) = _state.update { it.copy(searchQuery = value) }

    fun toggleParticipant(contact: Contact) = _state.update { current ->
        val id = contact.userId.value
        current.copy(
            selectedIds = if (id in current.selectedIds) current.selectedIds - id
            else current.selectedIds + id
        )
    }

    fun loadContacts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingContacts = true) }
            when (val result = contactRepository.contacts()) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoadingContacts = false,
                        contacts = result.data.contacts,
                        favorites = result.data.favorites,
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoadingContacts = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /** Validates the details step before the user walks to the participants step. */
    fun validateDetails(): Boolean {
        val error = Validators.displayName(_state.value.name, "name")
        if (error != null) {
            _state.update { it.copy(fieldErrors = mapOf(error.field to error.message)) }
            return false
        }
        return true
    }

    fun create() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val result = groupRepository.create(
                CreateGroupRequest(
                    name = current.name.trim(),
                    description = current.description.takeIf { it.isNotBlank() },
                    category = current.category,
                    currency = current.currency,
                    memberIds = current.selectedIds.map { UserId(it) },
                )
            )
            when (result) {
                is DataResult.Success -> _state.update {
                    it.copy(isSaving = false, createdGroup = result.data)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /** Clears the draft so the next "New group" starts blank. */
    fun reset() = _state.update {
        NewGroupState(contacts = it.contacts, favorites = it.favorites, isLoadingContacts = false)
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
}


