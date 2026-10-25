package com.elozino.splitfy.screens.groups.list

import com.elozino.splitfy.ui.components.RefreshableBox
import com.elozino.splitfy.ui.components.LoadErrorState
import com.elozino.splitfy.ui.components.SkeletonListItem
import com.elozino.splitfy.ui.components.SkeletonList
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.ui.components.SearchField
import com.elozino.splitfy.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel = koinViewModel(),
    onNavigateToNewGroup: () -> Unit,
    onNavigateToGroupDetail: (String) -> Unit,
    /** Off beside the navigation rail, whose own New group button would duplicate it. */
    showNewGroupButton: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-read on every entry: a group created or settled elsewhere must show here.
    LaunchedEffect(Unit) { viewModel.load() }

    GroupsListScreen(
        groups = state.groups.map { it.toItem() },
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        errorMessage = state.errorMessage,
        onRetry = viewModel::load,
        onCreateGroupClick = onNavigateToNewGroup,
        onGroupClick = { item -> onNavigateToGroupDetail(item.id) },
        showNewGroupButton = showNewGroupButton,
    )
}

/** Maps the API summary onto what the row renders. */
private fun GroupSummary.toItem() = GroupItemData(
    id = group.id.value,
    name = group.name,
    members = "${group.members.size} member" + if (group.members.size == 1) "" else "s",
    balance = yourBalance.abs().toPlainString() + " " + yourBalance.currency.code,
    isOwed = yourBalance.isPositive,
    isSettled = yourBalance.isZero,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsListScreen(
    groups: List<GroupItemData>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    errorMessage: String?,
    onRetry: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onGroupClick: (GroupItemData) -> Unit,
    showNewGroupButton: Boolean = true,
) {
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) groups else groups.filter { it.name.contains(query.trim(), ignoreCase = true) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (isSearching) {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = "Search groups",
                            onClose = { isSearching = false },
                        )
                    } else {
                        Text("Groups", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (!isSearching && groups.isNotEmpty()) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (showNewGroupButton) FloatingActionButton(
                onClick = onCreateGroupClick,
                containerColor = AppTheme.colors.brand,
                contentColor = AppTheme.colors.onBrand,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Group")
            }
        }
    ) { padding ->
        RefreshableBox(isRefreshing = isRefreshing, onRefresh = onRefresh, indicatorPadding = padding) {
            if (isLoading && groups.isEmpty()) {
                // Card-shaped placeholders, so the real cards replace them in place.
                SkeletonList(
                    modifier = Modifier.padding(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(LayoutDirection.Ltr),
                        end = padding.calculateEndPadding(LayoutDirection.Ltr)
                    ),
                    count = 5,
                    contentPadding = PaddingValues(16.dp),
                    spacing = 12.dp,
                ) { index ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        SkeletonListItem(
                            modifier = Modifier.padding(16.dp),
                            titleFraction = 0.45f + (index % 3) * 0.1f,
                            subtitleFraction = 0.3f,
                        )
                    }
                }
            } else if (groups.isEmpty() && errorMessage != null) {
                LoadErrorState(errorMessage, onRetry = onRetry, modifier = Modifier.padding(padding))
            } else if (groups.isEmpty()) {
                EmptyGroupsState(onCreateGroupClick, padding)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.calculateTopPadding(),
                            start = padding.calculateStartPadding(LayoutDirection.Ltr),
                            end = padding.calculateEndPadding(LayoutDirection.Ltr)
                        ),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    errorMessage?.let { message ->
                        item {
                            Text(
                                text = message,
                                color = AppTheme.colors.negative,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                text = "No groups match \"${query.trim()}\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    items(shown, key = { it.id }) { group ->
                        GroupItem(group, onClick = { onGroupClick(group) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGroupsState(onCreateClick: () -> Unit, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = padding.calculateTopPadding(),
                start = padding.calculateStartPadding(LayoutDirection.Ltr),
                end = padding.calculateEndPadding(LayoutDirection.Ltr)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Empty state illustration
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Back Clipboard
            Box(
                modifier = Modifier
                    .size(100.dp, 130.dp)
                    .offset(x = (-20).dp, y = (-10).dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-8).dp)
                        .size(40.dp, 12.dp)
                        .background(AppTheme.colors.brand.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                )
            }
            
            // Front Clipboard
            Box(
                modifier = Modifier
                    .size(110.dp, 140.dp)
                    .offset(x = 10.dp, y = 10.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            ) {
               Box(
                   modifier = Modifier
                       .align(Alignment.TopCenter)
                       .offset(y = (-10).dp)
                       .size(50.dp, 15.dp)
                       .background(AppTheme.colors.brand, RoundedCornerShape(4.dp))
               )
            }
        }
        
        Text(
            text = "Empty",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "You haven't created any group yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        
        Button(
            onClick = onCreateClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
        ) {
            Text("Create a New Group", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GroupItem(data: GroupItemData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.brand.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = AppTheme.colors.brand)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // One line, so a long name never runs into the amount beside it.
                Text(
                    text = data.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = data.members,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (data.isSettled) {
                    Text(text = "Settled up", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        text = data.balance,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (data.isOwed) AppTheme.colors.positive else AppTheme.colors.negative
                    )
                    Text(
                        text = if (data.isOwed) "You are owed" else "You owe",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

