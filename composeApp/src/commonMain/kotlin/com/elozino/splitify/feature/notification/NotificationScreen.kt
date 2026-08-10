package com.elozino.splitify.feature.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitify.feature.activity.presentation.NotificationViewModel
import com.elozino.splitify.shared.api.dto.AppNotification
import com.elozino.splitify.shared.api.dto.NotificationCategory
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: NotificationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        TextButton(onClick = viewModel::markAllRead) {
                            Text("Mark all read", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Grouped by day, newest first, straight from the API.
        val notifications = state.items
            .groupBy { it.createdAt.toString().take(10) }
            .map { (day, items) -> NotificationSection(day, items.map { it.toItem() }) }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFEBB1B))
            }
            return@Scaffold
        }

        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("You're all caught up", color = Color.Gray, fontSize = 16.sp)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            notifications.forEach { section ->
                item {
                    Text(
                        text = section.title,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                items(section.items) { notification ->
                    NotificationItem(
                        data = notification,
                        onClick = { viewModel.markRead(notification.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationItem(data: NotificationItemData, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, Color.LightGray.copy(alpha = 0.3f), CircleShape)
                .clip(CircleShape)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = data.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Black
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (data.isNew) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFEBB1B), CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = data.message,
                fontSize = 13.sp,
                color = Color.Gray,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = data.time,
                fontSize = 12.sp,
                color = Color.Gray.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.LightGray,
            modifier = Modifier.size(20.dp)
        )
    }
}

data class NotificationSection(
    val title: String,
    val items: List<NotificationItemData>
)

data class NotificationItemData(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val message: String,
    val time: String,
    val isNew: Boolean = false
)

/** Maps an API notification onto the row model, picking an icon per category. */
private fun AppNotification.toItem() = NotificationItemData(
    id = id,
    icon = when (category) {
        NotificationCategory.SECURITY -> Icons.Outlined.Security
        NotificationCategory.PAYMENT -> Icons.Outlined.CreditCard
        NotificationCategory.GROUP -> Icons.Outlined.Event
        NotificationCategory.PROMOTION -> Icons.Outlined.StarOutline
        NotificationCategory.SYSTEM -> Icons.Outlined.NewReleases
    },
    title = title,
    message = message,
    time = createdAt.toString().drop(11).take(5),
    isNew = !isRead,
)
