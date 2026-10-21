package com.elozino.splitfy.screens.contacts.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.Contact
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.repositories.ContactRepository
import com.elozino.splitfy.repositories.UserRepository
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

/**
 * Contacts are people the user already shares a group with. The directory search is
 * separate and only runs once the query is long enough for the backend to accept —
 * short prefixes would let anyone enumerate the user table.
 */
class ContactsViewModel(
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsState())
    val state: StateFlow<ContactsState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        load()
        observeQuery()
    }

    fun load() = fetch(isRefresh = false)

    /** Pull-to-refresh: reloads while what's on screen stays put, so no placeholders flash. */
    fun refresh() = fetch(isRefresh = true)

    private fun fetch(isRefresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !isRefresh, isRefreshing = isRefresh, errorMessage = null) }
            when (val result = contactRepository.contacts(_state.value.query.takeIf { q -> q.isNotBlank() })) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        contacts = result.data.contacts,
                        favorites = result.data.favorites,
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        queryFlow.value = value
    }

    fun toggleFavorite(contact: Contact) {
        viewModelScope.launch {
            val result = contactRepository.setFavorite(contact.userId.value, !contact.isFavorite)
            when (result) {
                is DataResult.Success -> _state.update {
                    it.copy(contacts = result.data.contacts, favorites = result.data.favorites)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    fun navigateTo(step: ContactStep) = _state.update { it.copy(currentStep = step) }

    fun selectContact(contact: ContactData) = _state.update {
        it.copy(selectedContact = contact, currentStep = ContactStep.DETAIL)
    }

    fun clearSelectedContact() = _state.update {
        it.copy(selectedContact = null, currentStep = ContactStep.LIST, isDeleteSuccessVisible = false)
    }

    fun onTabSelected(index: Int) = _state.update { it.copy(selectedTab = index) }

    fun setDeleteDialogVisible(isVisible: Boolean) =
        _state.update { it.copy(isDeleteDialogVisible = isVisible) }

    fun confirmDelete() = _state.update {
        it.copy(isDeleteDialogVisible = false, isDeleteSuccessVisible = true)
    }

    fun onNewContactNameChange(value: String) = _state.update { it.copy(newContactName = value) }

    fun onNewContactEmailChange(value: String) = _state.update { it.copy(newContactEmail = value) }

    fun setAddSuccessVisible(isVisible: Boolean) =
        _state.update { it.copy(isAddSuccessVisible = isVisible) }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query ->
                    // Filtering the user's own contacts is local and instant.
                    load()

                    if (query.trim().length < MIN_DIRECTORY_QUERY) {
                        _state.update { it.copy(directoryResults = emptyList(), isSearching = false) }
                        return@collect
                    }
                    _state.update { it.copy(isSearching = true) }
                    when (val result = userRepository.search(query.trim())) {
                        is DataResult.Success -> _state.update {
                            it.copy(isSearching = false, directoryResults = result.data)
                        }
                        is DataResult.Failure -> _state.update {
                            it.copy(isSearching = false, directoryResults = emptyList())
                        }
                    }
                }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L

        /** Matches the server's minimum; searching shorter would just 400. */
        const val MIN_DIRECTORY_QUERY = 3
    }
}

