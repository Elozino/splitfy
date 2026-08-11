package com.elozino.splitify.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitify.domain.model.GroupCategory
import com.elozino.splitify.ui.theme.primaryLight
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: GroupSettingsViewModel = koinViewModel { parametersOf(groupId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(SettingsStep.MENU) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    // Leaving or archiving takes the user out of the group entirely.
    LaunchedEffect(state.closed) {
        if (state.closed) onNavigateBack()
    }

    when (step) {
        SettingsStep.MENU -> {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(state.groupName, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp)
                ) {
                    if (state.canManage) {
                        SettingsItem("Edit Group", Icons.Default.Edit, onClick = { step = SettingsStep.EDIT })
                    }
                    SettingsItem("Leave Group", Icons.AutoMirrored.Filled.Logout, onClick = { showLeaveDialog = true })
                    if (state.canManage) {
                        SettingsItem(
                            "Archive Group",
                            Icons.Default.Delete,
                            isError = true,
                            onClick = { showDeleteDialog = true }
                        )
                    }

                    state.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(message, color = Color(0xFFD32F2F), fontSize = 14.sp)
                    }
                }
            }
        }
        SettingsStep.EDIT -> {
            EditGroupScreen(
                state = state,
                viewModel = viewModel,
                onNavigateBack = { step = SettingsStep.MENU },
                onSave = { step = SettingsStep.MENU }
            )
        }
    }

    if (showLeaveDialog) {
        ConfirmationDialog(
            title = "Leave Group",
            text = "Leave " + state.groupName + "? You can only leave once your balance is settled.",
            confirmLabel = "Yes, Leave Group",
            onConfirm = {
                showLeaveDialog = false
                viewModel.leaveGroup()
            },
            onDismiss = { showLeaveDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Archive Group",
            text = "Archive " + state.groupName + "? It disappears from your list, " +
                "but the expense history stays intact so balances remain auditable.",
            confirmLabel = "Yes, Archive Group",
            isError = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.archiveGroup()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

enum class SettingsStep {
    MENU, EDIT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditGroupScreen(
    state: GroupSettingsState,
    viewModel: GroupSettingsViewModel,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
) {
    val title = state.name
    val description = state.description
    val selectedCategory = state.category

    // Return to the menu once the change has landed on the server.
    LaunchedEffect(state.saved) {
        if (state.saved) onSave()
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Group", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, primaryLight)
                ) {
                    Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Cover Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(32.dp)
                        .background(primaryLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Title", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextField(
                value = title,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedContainerColor = Color(0xFFF5F5F5),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Description", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextField(
                value = description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 100.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedContainerColor = Color(0xFFF5F5F5),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Currency", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF5F5F5)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(state.group?.currency?.code ?: "-", fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val categories = listOf(
                GroupCategory.TRIP to "Trip ✈️",
                GroupCategory.HOME to "Home 👨‍👩‍👧‍👦",
                GroupCategory.COUPLE to "Couple 💑",
                GroupCategory.EVENT to "Event 🗓️", 
                GroupCategory.PROJECT to "Project 💼",
                GroupCategory.OTHER to "Other 🧩"
            )
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (category, label) ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onCategoryChange(category) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryLight,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    isError: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = if (isError) Color.Red else Color.Black) },
        text = { Text(text, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (isError) Color.Red else primaryLight),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(confirmLabel, color = if (isError) Color.White else Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, primaryLight)
            ) {
                Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun SettingsItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isError: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isError) Color.Red.copy(alpha = 0.1f) else Color.LightGray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isError) Color.Red else Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = if (isError) Color.Red else Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
