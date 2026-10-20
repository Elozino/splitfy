package com.elozino.splitfy.screens.groups.create.shared

import com.elozino.splitfy.api.dto.Contact
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupCategory

data class NewGroupState(
    val name: String = "",
    val description: String = "",
    val category: GroupCategory = GroupCategory.TRIP,
    val currency: CurrencyCode = CurrencyCode.NGN,
    val contacts: List<Contact> = emptyList(),
    val favorites: List<Contact> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isCurrencyMenuVisible: Boolean = false,
    val selectedParticipantsTab: Int = 0,
    val isLoadingContacts: Boolean = true,
    val isSaving: Boolean = false,
    val createdGroup: Group? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
) {
    val canContinue: Boolean get() = name.isNotBlank()

    fun visibleContacts(favoritesOnly: Boolean): List<Contact> {
        val base = if (favoritesOnly) favorites else contacts
        if (searchQuery.isBlank()) return base
        val needle = searchQuery.trim().lowercase()
        return base.filter {
            it.displayName.lowercase().contains(needle) || it.email.lowercase().contains(needle)
        }
    }

    fun isSelected(contact: Contact): Boolean = contact.userId.value in selectedIds
}


