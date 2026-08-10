package com.elozino.splitify.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.api.dto.Contact
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.User
import com.elozino.splitify.repositories.ContactRepository
import com.elozino.splitify.repositories.UserRepository
import com.elozino.splitify.userMessage
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

data class ContactsUiState(
    val isLoading: Boolean = true,
    val contacts: List<Contact> = emptyList(),
    val favorites: List<Contact> = emptyList(),
    val query: String = "",
    /** People not yet sharing a group, found by the directory search. */
    val directoryResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && contacts.isEmpty()

    fun visible(showFavoritesOnly: Boolean): List<Contact> =
        if (showFavoritesOnly) favorites else contacts
}

/**
 * Contacts are people the user already shares a group with. The directory search is
 * separate and only runs once the query is long enough for the backend to accept —
 * short prefixes would let anyone enumerate the user table.
 */
class ContactsViewModel(
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        load()
        observeQuery()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = contactRepository.contacts(_state.value.query.takeIf { q -> q.isNotBlank() })) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        contacts = result.data.contacts,
                        favorites = result.data.favorites,
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.userMessage())
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
