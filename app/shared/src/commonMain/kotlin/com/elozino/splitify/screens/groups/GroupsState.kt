package com.elozino.splitify.screens

import com.elozino.splitify.api.dto.GroupSummary

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
