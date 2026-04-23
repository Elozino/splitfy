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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        val notifications = listOf(
            NotificationSection(
                title = "Today",
                items = listOf(
                    NotificationItemData(
                        icon = Icons.Outlined.NewReleases,
                        title = "New Updates Available!",
                        message = "Update the Splitify and enjoy new features for a better split bill experi...",
                        time = "09:41 AM",
                        isNew = true
                    ),
                    NotificationItemData(
                        icon = Icons.Outlined.Security,
                        title = "Enable 2FA for Security!",
                        message = "Use two-factor authentication for extra security on your account.",
                        time = "09:10 AM",
                        isNew = true
                    )
                )
            ),
            NotificationSection(
                title = "Yesterday",
                items = listOf(
                    NotificationItemData(
                        icon = Icons.Outlined.CreditCard,
                        title = "Multiple Payments Support!",
                        message = "Now you can add multiple cards. Go to Account -> Payment methods to add ano...",
                        time = "09:47 AM"
                    ),
                    NotificationItemData(
                        icon = Icons.Outlined.Event,
                        title = "Christmas and New Year Event!",
                        message = "We offer a special event for you on the special day of Christmas.",
                        time = "16:50 PM"
                    )
                )
            ),
            NotificationSection(
                title = "Dec 18, 2023",
                items = listOf(
                    NotificationItemData(
                        icon = Icons.Outlined.StarOutline,
                        title = "Premium Features Unlocked!",
                        message = "Upgrade to Splitify Premium and unlock exclusive features for even smoother bill...",
                        time = "13:41 PM"
                    )
                )
            )
        )

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
                    NotificationItem(notification)
                }
            }
        }
    }
}

@Composable
fun NotificationItem(data: NotificationItemData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
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
    val icon: ImageVector,
    val title: String,
    val message: String,
    val time: String,
    val isNew: Boolean = false
)
