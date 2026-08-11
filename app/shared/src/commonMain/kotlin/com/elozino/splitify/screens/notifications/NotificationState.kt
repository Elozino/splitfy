package com.elozino.splitify.screens

import com.elozino.splitify.api.dto.AppNotification

data class NotificationState(
    val isLoading: Boolean = true,
    val items: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
