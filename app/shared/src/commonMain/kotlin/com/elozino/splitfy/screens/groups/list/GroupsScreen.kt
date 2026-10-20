package com.elozino.splitfy.screens.groups.list

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.ui.theme.primaryLight
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel = koinViewModel(),
    onNavigateToNewGroup: () -> Unit,
    onNavigateToGroupDetail: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-read on every entry: a group created or settled elsewhere must show here.
    LaunchedEffect(Unit) { viewModel.load() }

    GroupsListScreen(
        groups = state.groups.map { it.toItem() },
        isLoading = state.isLoading,
        errorMessage = state.errorMessage,
        onCreateGroupClick = onNavigateToNewGroup,
        onGroupClick = { item -> onNavigateToGroupDetail(item.id) }
    )
}

/** Maps the API summary onto what the row renders. */
private fun GroupSummary.toItem() = GroupItemData(
    id = group.id.value,
    name = group.name,
    members = "${group.members.size} member" + if (group.members.size == 1) "" else "s",
    balance = yourBalance.abs().toPlainString() + " " + yourBalance.currency.code,
    isOwed = yourBalance.isPositive
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsListScreen(
    groups: List<GroupItemData>,
    isLoading: Boolean,
    errorMessage: String?,
    onCreateGroupClick: () -> Unit,
    onGroupClick: (GroupItemData) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Groups", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGroupClick,
                containerColor = primaryLight,
                contentColor = Color.Black,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Group")
            }
        }
    ) { padding ->
        if (isLoading && groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(LayoutDirection.Ltr),
                    end = padding.calculateEndPadding(LayoutDirection.Ltr)
                ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = primaryLight)
            }
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
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                items(groups, key = { it.id }) { group ->
                    GroupItem(group, onClick = { onGroupClick(group) })
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
                    .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-8).dp)
                        .size(40.dp, 12.dp)
                        .background(primaryLight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                )
            }
            
            // Front Clipboard
            Box(
                modifier = Modifier
                    .size(110.dp, 140.dp)
                    .offset(x = 10.dp, y = 10.dp)
                    .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp))
            ) {
               Box(
                   modifier = Modifier
                       .align(Alignment.TopCenter)
                       .offset(y = (-10).dp)
                       .size(50.dp, 15.dp)
                       .background(primaryLight, RoundedCornerShape(4.dp))
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
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        
        Button(
            onClick = onCreateClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
        ) {
            Text("Create a New Group", color = Color.Black, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    .background(primaryLight.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = primaryLight)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = data.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = data.members, fontSize = 12.sp, color = Color.Gray)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = data.balance,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (data.isOwed) Color(0xFF4CAF50) else Color(0xFFE57373)
                )
                Text(
                    text = if (data.isOwed) "You are owed" else "You owe",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray)
            }
        }
    }
}

