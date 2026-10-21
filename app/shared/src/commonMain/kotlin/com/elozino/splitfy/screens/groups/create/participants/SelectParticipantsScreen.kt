package com.elozino.splitfy.screens.groups.create.participants

import com.elozino.splitfy.ui.components.SkeletonListItem
import com.elozino.splitfy.ui.components.SkeletonList
import com.elozino.splitfy.ui.components.SkeletonBlock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.elozino.splitfy.ui.theme.AppTheme
import com.elozino.splitfy.screens.groups.create.shared.NewGroupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectParticipantsScreen(
    viewModel: NewGroupViewModel,
    onNavigateBack: () -> Unit,
    onFinish: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val searchQuery = state.searchQuery
    val contacts = state.visibleContacts(favoritesOnly = state.selectedParticipantsTab == 1)

    // Leave the flow only once the group actually exists on the server.
    LaunchedEffect(state.createdGroup) {
        if (state.createdGroup != null) {
            viewModel.reset()
            onFinish()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Select Participants", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.brand)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = viewModel::create,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
                ) {
                    Text(
                        text = if (state.isSaving) "Saving…" else "Save",
                        color = AppTheme.colors.onBrand,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    // Search Bar
                    TextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        placeholder = { Text("Search contact", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = AppTheme.colors.field,
                            focusedContainerColor = AppTheme.colors.field,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                val selectedContacts = state.contacts.filter { state.isSelected(it) }
                if (selectedContacts.isNotEmpty()) {
                    item {
                        // Selected horizontal list
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(selectedContacts, key = { it.userId.value }) { contact ->
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(MaterialTheme.colorScheme.inverseSurface, CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            .clickable { viewModel.toggleParticipant(contact) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Custom Tabs
                    Surface(
                        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = AppTheme.colors.field
                    ) {
                        Row {
                            val tabs = listOf("All Contacts", "Favorites")
                            tabs.forEachIndexed { index, title ->
                                val isSelected = state.selectedParticipantsTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AppTheme.colors.brand else Color.Transparent)
                                        .clickable { viewModel.onParticipantsTabSelected(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AppTheme.colors.onBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (state.isLoadingContacts) {
                    item {
                        // Contact rows end in a checkbox, so a small square stands in for it.
                        SkeletonList(count = 6, contentPadding = PaddingValues(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SkeletonListItem(modifier = Modifier.weight(1f), avatarSize = 48.dp, trailing = false)
                                SkeletonBlock(Modifier.size(22.dp), RoundedCornerShape(6.dp))
                            }
                        }
                    }
                }

                if (!state.isLoadingContacts && contacts.isEmpty()) {
                    item {
                        Text(
                            text = "No contacts yet. You can add people to the group later.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contacts, key = { it.userId.value }) { contact ->
                    val isSelected = state.isSelected(contact)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .clickable { viewModel.toggleParticipant(contact) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(contact.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            
            // Alphabet index
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items("ABCDEFGHIJKLMNOPQRSTUVWXYZ".toList()) { char ->
                    Text(text = char.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}



