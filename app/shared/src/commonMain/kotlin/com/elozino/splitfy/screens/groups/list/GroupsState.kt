package com.elozino.splitfy.screens.groups.list

import com.elozino.splitfy.api.dto.GroupSummary

data class GroupItemData(
    val id: String,
    val name: String,
    val members: String,
    val balance: String,
    val isOwed: Boolean,
    val isSettled: Boolean = false,
)

data class GroupsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val groups: List<GroupSummary> = emptyList(),
    val includeArchived: Boolean = false,
    val errorMessage: String? = null,
    val isCreating: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}
