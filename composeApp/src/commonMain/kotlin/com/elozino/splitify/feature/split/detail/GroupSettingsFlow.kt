package com.elozino.splitify.feature.split.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.ui.theme.primaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: String,
    onNavigateBack: () -> Unit
) {
    var step by remember { mutableStateOf(SettingsStep.MENU) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    when (step) {
        SettingsStep.MENU -> {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Trip to Japan 🇯🇵", fontWeight = FontWeight.Bold) },
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
                    SettingsItem("Edit Group", Icons.Default.Edit, onClick = { step = SettingsStep.EDIT })
                    SettingsItem("Leave Group", Icons.AutoMirrored.Filled.Logout, onClick = { showLeaveDialog = true })
                    SettingsItem("Delete Group", Icons.Default.Delete, isError = true, onClick = { showDeleteDialog = true })
                }
            }
        }
        SettingsStep.EDIT -> {
            EditGroupScreen(
                onNavigateBack = { step = SettingsStep.MENU },
                onSave = { step = SettingsStep.MENU }
            )
        }
    }

    if (showLeaveDialog) {
        ConfirmationDialog(
            title = "Leave Group",
            text = "Are you sure you want to leave from the group \"Trip to Japan\"?",
            confirmLabel = "Yes, Leave Group",
            onConfirm = { showLeaveDialog = false },
            onDismiss = { showLeaveDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete Group",
            text = "Are you sure you want to delete the group \"Trip to Japan\"?\nAll history and balances will be lost forever.",
            confirmLabel = "Yes, Delete Group",
            isError = true,
            onConfirm = { showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

enum class SettingsStep {
    MENU, EDIT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditGroupScreen(onNavigateBack: () -> Unit, onSave: () -> Unit) {
    var title by remember { mutableStateOf("Trip to Japan 🇯🇵") }
    var description by remember { mutableStateOf("Holidaying with college friends in Japan") }
    var selectedCategory by remember { mutableStateOf("Trip") }
    
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
                    onClick = onSave,
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
                onValueChange = { title = it },
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
                onValueChange = { description = it },
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
                    Text("USD", fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val categories = listOf(
                "Trip" to "✈️", 
                "Family" to "👨‍👩‍👧‍👦", 
                "Couple" to "💑", 
                "Event" to "🗓️", 
                "Project" to "💼", 
                "Other" to "🧩"
            )
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (name, emoji) ->
                    val isSelected = selectedCategory == name
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = name },
                        label = { Text("$name $emoji") },
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
