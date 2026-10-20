package com.elozino.splitfy.screens.groups.settings

import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupCategory
import com.elozino.splitfy.domain.model.UserId

enum class GroupSettingsStep {
    MENU,
    EDIT,
}

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
    val step: GroupSettingsStep = GroupSettingsStep.MENU,
    val isDeleteDialogVisible: Boolean = false,
    val isLeaveDialogVisible: Boolean = false,
) {
    val groupName: String get() = group?.name ?: "Group"
    val canManage: Boolean
        get() = group?.canManage(UserId(currentUserId.orEmpty())) ?: false
}

