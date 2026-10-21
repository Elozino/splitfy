package com.elozino.splitfy.screens.contacts.main

import com.elozino.splitfy.api.dto.Contact
import com.elozino.splitfy.domain.model.User

enum class ContactStep { LIST, SEARCH, DETAIL, ADD_CONTACT, SEARCH_RESULT }

data class ContactData(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val isFavorite: Boolean = false,
    val balanceLabel: String? = null,
    val sharedGroups: Int = 0,
)

data class ContactsState(
    val isLoading: Boolean = true,
    /** A pull-to-refresh is in flight; content stays on screen meanwhile. */
    val isRefreshing: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val favorites: List<Contact> = emptyList(),
    val query: String = "",
    val directoryResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val currentStep: ContactStep = ContactStep.LIST,
    val selectedContact: ContactData? = null,
    val selectedTab: Int = 0,
    val isDeleteDialogVisible: Boolean = false,
    val isDeleteSuccessVisible: Boolean = false,
    val newContactName: String = "",
    val newContactEmail: String = "",
    val isAddSuccessVisible: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && contacts.isEmpty()

    fun visible(showFavoritesOnly: Boolean): List<Contact> =
        if (showFavoritesOnly) favorites else contacts
}

