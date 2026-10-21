package com.elozino.splitfy.screens.notifications.list

import androidx.compose.ui.graphics.vector.ImageVector
import com.elozino.splitfy.api.dto.AppNotification

data class NotificationSection(
    val title: String,
    val items: List<NotificationItemData>,
)

data class NotificationItemData(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val message: String,
    val time: String,
    val isNew: Boolean = false,
)

data class NotificationState(
    val isLoading: Boolean = true,
    /** A pull-to-refresh is in flight; content stays on screen meanwhile. */
    val isRefreshing: Boolean = false,
    val items: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
