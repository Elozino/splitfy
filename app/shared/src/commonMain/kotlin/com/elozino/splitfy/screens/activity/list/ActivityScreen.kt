package com.elozino.splitfy.screens.activity.list

import com.elozino.splitfy.ui.components.RefreshableBox
import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme
import com.elozino.splitfy.ui.components.LoadErrorState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import com.elozino.splitfy.ui.components.SkeletonText
import com.elozino.splitfy.ui.components.SkeletonListItem
import com.elozino.splitfy.ui.components.SkeletonList
import com.elozino.splitfy.ui.components.SkeletonBlock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.api.dto.ActivityFilter
import com.elozino.splitfy.ui.components.SearchField
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    /** Null when the wallet is off; rows then open Settle Up for their group instead. */
    onNavigateToPay: (() -> Unit)? = null,
    onNavigateToRequest: (() -> Unit)? = null,
    onNavigateToSettleUp: (groupId: String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf(
        ActivityFilter.ALL to "All",
        ActivityFilter.YOU_OWE to "You Owe",
        ActivityFilter.OWED_TO_YOU to "Owe to you",
    )
    val selectedTabIndex = tabs.indexOfFirst { it.first == state.filter }.coerceAtLeast(0)
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (isSearching) {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = "Search people or groups",
                            onClose = { isSearching = false },
                        )
                    } else {
                        Text("Activity", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        }
    ) { padding ->
        RefreshableBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, indicatorPadding = padding) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Tab Row: across the width on a phone; on a wider window the pills keep
                // their size at the start, rather than stretching into long bars.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fillWidth = activityFiltersFillWidth(maxWidth)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEachIndexed { index, (filter, title) ->
                            val isSelected = selectedTabIndex == index
                            Button(
                                onClick = { viewModel.onFilterChange(filter) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) AppTheme.colors.brand else Color.Transparent,
                                    contentColor = if (isSelected) AppTheme.colors.onBrand else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = if (fillWidth) Modifier.weight(1f) else Modifier.widthIn(min = ActivityFilterMinWidth),
                                contentPadding = if (fillWidth) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp),
                                elevation = null
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Grouped by day, newest first; every line comes from the ledger.
                val needle = query.trim()
                val activities = state.entries
                    .filter { entry ->
                        needle.isEmpty() || listOf(entry.fromName, entry.toName, entry.groupName)
                            .any { it.contains(needle, ignoreCase = true) }
                    }
                    .groupBy { it.lastActivityAt.toString().take(10) }
                    .map { (day, entries) -> ActivitySection(day, entries.map { it.toItem(state.currentUserId) }) }

                if (state.isLoading) {
                    // A day heading, then rows ending in an action pill, as the real list.
                    SkeletonList(count = 6, spacing = 22.dp) { index ->
                        if (index == 0) {
                            SkeletonText(0.3f, height = 12.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SkeletonListItem(
                                    modifier = Modifier.weight(1f),
                                    avatarSize = null,
                                    titleFraction = 0.45f + (index % 3) * 0.1f,
                                )
                                Spacer(Modifier.width(12.dp))
                                SkeletonBlock(Modifier.width(64.dp).height(32.dp), RoundedCornerShape(20.dp))
                            }
                        }
                    }
                    return@Column
                }

                val loadError = state.errorMessage
                if (loadError != null && state.entries.isEmpty()) {
                    LoadErrorState(loadError, onRetry = viewModel::load)
                    return@Column
                }

                if (state.isEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing outstanding 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    }
                    return@Column
                }

                if (activities.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing matches \"$needle\"", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    }
                    return@Column
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    activities.forEach { section ->
                        item {
                            Text(
                                text = section.date,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        items(section.items) { item ->
                            ActivityItem(
                                data = item,
                                onActionClick = {
                                    when {
                                        item.isCompleted -> Unit
                                        // Without a wallet, both directions settle by recording a payment.
                                        onNavigateToPay == null || onNavigateToRequest == null ->
                                            onNavigateToSettleUp(item.groupId)
                                        item.actionLabel == "Pay" -> onNavigateToPay()
                                        else -> onNavigateToRequest()
                                    }
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityItem(data: ActivityItemData, onActionClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "${data.status} ${data.target}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = data.amount,
            color = when {
                data.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                data.direction == DebtDirection.OWED_TO_YOU -> AppTheme.colors.positive
                data.direction == DebtDirection.YOU_OWE -> AppTheme.colors.negative
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Only the payer or payee can settle a debt, so others see the amount alone.
        val actionLabel = data.actionLabel
        if (actionLabel != null) {
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (data.isCompleted) Color.Transparent else AppTheme.colors.brand,
                    contentColor = if (data.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else AppTheme.colors.onBrand
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .then(
                        if (data.isCompleted) Modifier.background(Color.Transparent).padding(0.dp) else Modifier
                    ),
                border = if (data.isCompleted) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant) else null,
                elevation = null
            ) {
                Text(text = actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

