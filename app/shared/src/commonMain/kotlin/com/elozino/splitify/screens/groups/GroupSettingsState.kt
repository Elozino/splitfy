package com.elozino.splitify.screens

import com.elozino.splitify.domain.model.Group
import com.elozino.splitify.domain.model.GroupCategory
import com.elozino.splitify.domain.model.UserId

data class GroupSettingsState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val name: String = "",
    val description: String = "",
    val category: GroupCategory = GroupCategory.OTHER,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val closed: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val currentUserId: String? = null,
) {
    val groupName: String get() = group?.name ?: "Group"
    val canManage: Boolean
        get() = group?.canManage(UserId(currentUserId.orEmpty())) ?: false
}
