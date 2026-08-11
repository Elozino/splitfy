package com.elozino.splitify.screens

import com.elozino.splitify.api.dto.Contact
import com.elozino.splitify.domain.model.User

data class ContactsState(
    val isLoading: Boolean = true,
    val contacts: List<Contact> = emptyList(),
    val favorites: List<Contact> = emptyList(),
    val query: String = "",
    val directoryResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && contacts.isEmpty()

    fun visible(showFavoritesOnly: Boolean): List<Contact> =
        if (showFavoritesOnly) favorites else contacts
}
