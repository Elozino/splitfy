package com.elozino.splitfy.screens.home.main

import com.elozino.splitfy.ui.components.RefreshableBox
import com.elozino.splitfy.ui.components.LoadErrorState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.FeatureFlags
import com.elozino.splitfy.ui.components.SkeletonBlock
import com.elozino.splitfy.ui.components.SkeletonList
import com.elozino.splitfy.ui.components.SkeletonListItem
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.domain.model.LedgerEntry
import com.elozino.splitfy.screens.home.main.HomeState
import com.elozino.splitfy.screens.home.main.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true, showSystemUi = true)
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onNavigateToNotification: () -> Unit = {},
    onNavigateToActivity: () -> Unit = {},
    onNavigateToPay: () -> Unit = {},
    onNavigateToRequest: () -> Unit = {},
    onNavigateToTopUp: () -> Unit = {},
    onNavigateToWithdraw: () -> Unit = {},
    onNavigateToTransactionHistory: () -> Unit = {},
    onNavigateToNewGroup: () -> Unit = {},
    onNavigateToGroups: () -> Unit = {},
    onNavigateToGroupDetail: (String) -> Unit = {},
    onNavigateToVerifyEmail: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "splitfy",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = "Logo",
                        modifier = Modifier.padding(start = 16.dp).size(28.dp),
                        tint = Color.Black
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToNotification) {
                        BadgedBox(
                            badge = {
                                if (state.unreadNotifications > 0) {
                                    Badge { Text(state.unreadNotifications.coerceAtMost(99).toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                modifier = Modifier.size(28.dp),
                                tint = Color.Black
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFEBB1B)
                )
            )
        }
    ) { padding ->
        RefreshableBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, indicatorPadding = padding) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(
                            layoutDirection = LayoutDirection.Ltr
                        ),
                        end = padding.calculateEndPadding(
                            layoutDirection = LayoutDirection.Ltr
                        )
                    )
            ) {
                if (state.user?.emailVerified == false) {
                    VerifyEmailBanner(onClick = onNavigateToVerifyEmail)
                }
                if (FeatureFlags.WALLET) {
                    BalanceHeader(
                        state = state,
                        onSendClick = onNavigateToPay,
                        onRequestClick = onNavigateToRequest,
                        onTopUpClick = onNavigateToTopUp,
                        onWithdrawClick = onNavigateToWithdraw,
                        onHistoryClick = onNavigateToTransactionHistory
                    )
                    ActivitySection(
                        state = state,
                        modifier = Modifier.weight(1f),
                        onViewAllClick = onNavigateToActivity
                    )
                } else {
                    PositionHeader(
                        state = state,
                        onNewGroupClick = onNavigateToNewGroup,
                        onGroupsClick = onNavigateToGroups,
                        onActivityClick = onNavigateToActivity,
                    )
                    GroupsSection(
                        state = state,
                        onRetry = viewModel::load,
                        modifier = Modifier.weight(1f),
                        onViewAllClick = onNavigateToGroups,
                        onGroupClick = onNavigateToGroupDetail,
                        onNewGroupClick = onNavigateToNewGroup,
                    )
                }
            }
        }
    }
}

@Composable
fun BalanceHeader(
    state: HomeState,
    onSendClick: () -> Unit = {},
    onRequestClick: () -> Unit = {},
    onTopUpClick: () -> Unit = {},
    onWithdrawClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {}
) {
    Surface(
        color = Color(0xFFFEBB1B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.walletBalance.toPlainString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = state.walletBalance.currency.code,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp),
                    color = Color.Black
                )
            }

            Text(
                text = "Available balance",
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )

            if (!state.totalOwedToYou.isZero || !state.totalYouOwe.isZero) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Owed to you ${state.totalOwedToYou.toPlainString()}  ·  " +
                            "You owe ${state.totalYouOwe.toPlainString()}",
                    fontSize = 13.sp,
                    color = Color.Black.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ActionItem(icon = Icons.Default.NearMe, label = "Send", onClick = onSendClick)
                ActionItem(icon = Icons.Default.ArrowCircleDown, label = "Request", onClick = onRequestClick)
                ActionItem(icon = Icons.Default.Download, label = "Top Up", onClick = onTopUpClick)
                ActionItem(icon = Icons.AutoMirrored.Filled.Logout, label = "Withdraw", onClick = onWithdrawClick)
                ActionItem(icon = Icons.Default.History, label = "History", onClick = onHistoryClick)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .border(1.2.dp, Color.Black.copy(alpha = 0.6f), CircleShape)
                .clip(CircleShape)
                .clickable { onClick() }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = Color.Black
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color.Black
        )
    }
}

@Composable
fun ActivitySection(
    state: HomeState,
    modifier: Modifier = Modifier,
    onViewAllClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onViewAllClick() }
            ) {
                Text(
                    text = "View All",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (state.isLoading) {
            SkeletonList(count = 4, contentPadding = PaddingValues(vertical = 16.dp)) { index ->
                SkeletonListItem(avatarSize = null, titleFraction = 0.5f + (index % 3) * 0.08f)
            }
            return@Column
        }

        if (!state.hasActivity) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.LightGray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Transactions",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You haven't made any transactions.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
            return@Column
        }

        Spacer(modifier = Modifier.height(8.dp))
        state.recentEntries.forEach { entry ->
            RecentEntryRow(entry)
        }
    }
}

/** One line of the wallet ledger, signed so credits and debits read at a glance. */
@Composable
private fun RecentEntryRow(entry: LedgerEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.counterpartyName ?: entry.description,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Text(
                text = entry.note ?: entry.type.name.lowercase().replace('_', ' '),
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
        Text(
            text = (if (entry.type.isCredit) "+ " else "- ") + entry.amount.toPlainString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (entry.type.isCredit) Color(0xFF2E7D32) else Color.Black
        )
    }
}

/**
 * The header when there is no wallet: where you stand across all your groups,
 * which is the number a bill-splitting app is opened to check.
 */
@Composable
fun PositionHeader(
    state: HomeState,
    onNewGroupClick: () -> Unit,
    onGroupsClick: () -> Unit,
    onActivityClick: () -> Unit,
) {
    val net = state.netPosition
    Surface(color = Color(0xFFFEBB1B)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            // Until the first load, show the shape of the number rather than "0.00 ·
            // You're all settled up", which reads as a real (and wrong) answer.
            if (state.isLoading && state.groups.isEmpty()) {
                SkeletonBlock(Modifier.width(220.dp).height(56.dp).padding(vertical = 4.dp), onAccent = true)
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBlock(Modifier.width(140.dp).height(14.dp), RoundedCornerShape(6.dp), onAccent = true)
            } else if (state.loadFailed) {
                Text(text = "—", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(
                    text = "Couldn't load your balance",
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            } else Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = net.abs().toPlainString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = net.currency.code,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp),
                    color = Color.Black
                )
            }
            if (!(state.isLoading && state.groups.isEmpty()) && !state.loadFailed) Text(
                text = when {
                    net.isPositive -> "You are owed overall"
                    net.isNegative -> "You owe overall"
                    else -> "You're all settled up"
                },
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )

            if (!state.totalOwedToYou.isZero && !state.totalYouOwe.isZero) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Owed to you ${state.totalOwedToYou.toPlainString()}  ·  " +
                        "You owe ${state.totalYouOwe.toPlainString()}",
                    fontSize = 13.sp,
                    color = Color.Black.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
            if (state.hasOtherCurrencies) {
                Text(
                    text = "Groups in other currencies are not included",
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionItem(icon = Icons.Default.GroupAdd, label = "New group", onClick = onNewGroupClick)
                ActionItem(icon = Icons.Default.Groups, label = "Groups", onClick = onGroupsClick)
                ActionItem(icon = Icons.Default.History, label = "Activity", onClick = onActivityClick)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun GroupsSection(
    state: HomeState,
    modifier: Modifier = Modifier,
    onViewAllClick: () -> Unit,
    onRetry: () -> Unit,
    onGroupClick: (String) -> Unit,
    onNewGroupClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your groups", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            if (state.groups.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onViewAllClick() }
                ) {
                    Text("View All", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (state.isLoading && state.groups.isEmpty()) {
            SkeletonList(count = 4, contentPadding = PaddingValues(vertical = 16.dp)) { index ->
                SkeletonListItem(avatarSize = null, titleFraction = 0.42f + (index % 3) * 0.1f)
            }
            return@Column
        }

        val loadError = state.errorMessage
        if (state.loadFailed && loadError != null) {
            LoadErrorState(loadError, onRetry = onRetry)
            return@Column
        }

        if (state.groups.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.LightGray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No groups yet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Create a group for a trip, a flat or a night out, then add what everyone spent.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNewGroupClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEBB1B)),
                ) {
                    Text("Create a group", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            return@Column
        }

        Spacer(modifier = Modifier.height(8.dp))
        state.groups
            .sortedByDescending { kotlin.math.abs(it.yourBalance.amount) }
            .take(HOME_GROUP_LIMIT)
            .forEach { summary -> GroupRow(summary, onClick = { onGroupClick(summary.group.id.value) }) }
    }
}

private const val HOME_GROUP_LIMIT = 5

/** Shown until the email is confirmed, since unconfirmed accounts can't be added by email. */
@Composable
private fun VerifyEmailBanner(onClick: () -> Unit) {
    Surface(color = Color(0xFFFFF3CD), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MarkEmailUnread, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Confirm your email so friends can add you to groups",
                fontSize = 13.sp,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Black)
        }
    }
}

@Composable
private fun GroupRow(summary: GroupSummary, onClick: () -> Unit) {
    val balance = summary.yourBalance
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.group.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            Text(
                text = when {
                    balance.isPositive -> "You are owed"
                    balance.isNegative -> "You owe"
                    else -> "Settled up"
                },
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
        if (!balance.isZero) {
            Text(
                text = "${balance.abs().toPlainString()} ${balance.currency.code}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (balance.isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

