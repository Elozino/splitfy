package com.elozino.splitify.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitify.api.dto.Contact
import com.elozino.splitify.ui.theme.primaryLight
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ContactScreen(
    onVisibilityChanged: (Boolean) -> Unit,
    viewModel: ContactsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var currentStep by remember { mutableStateOf(ContactStep.LIST) }
    var selectedContact by remember { mutableStateOf<ContactData?>(null) }
    val searchQuery = state.query

    LaunchedEffect(currentStep) {
        onVisibilityChanged(currentStep == ContactStep.LIST)
    }

    when (currentStep) {
        ContactStep.LIST -> ContactListScreen(
            contacts = state.contacts.map { it.toData() },
            favorites = state.favorites.map { it.toData() },
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onToggleFavorite = { data ->
                state.contacts.firstOrNull { it.userId.value == data.id }
                    ?.let(viewModel::toggleFavorite)
            },
            onSearchClick = { currentStep = ContactStep.SEARCH },
            onAddContactClick = { currentStep = ContactStep.ADD_CONTACT },
            onContactClick = { 
                selectedContact = it
                currentStep = ContactStep.DETAIL 
            }
        )
        ContactStep.SEARCH -> SearchContactScreen(
            query = searchQuery,
            onQueryChange = viewModel::onQueryChange,
            results = state.contacts.map { it.toData() },
            directoryResults = state.directoryResults.map { user ->
                ContactData(user.id.value, user.displayName, user.email)
            },
            onNavigateBack = { currentStep = ContactStep.LIST },
            onContactClick = {
                selectedContact = it
                currentStep = ContactStep.DETAIL
            }
        )
        ContactStep.DETAIL -> ContactDetailScreen(
            contact = selectedContact!!,
            onToggleFavorite = {
                state.contacts.firstOrNull { it.userId.value == selectedContact?.id }
                    ?.let(viewModel::toggleFavorite)
            },
            onNavigateBack = { currentStep = ContactStep.LIST },
            onDeleteSuccess = { 
                selectedContact = null
                currentStep = ContactStep.LIST
            }
        )
        ContactStep.ADD_CONTACT -> AddContactScreen(
            onNavigateBack = { currentStep = ContactStep.LIST },
            onContinue = { currentStep = ContactStep.SEARCH_RESULT }
        )
        ContactStep.SEARCH_RESULT -> SearchResultScreen(
            onNavigateBack = { currentStep = ContactStep.ADD_CONTACT },
            onAddSuccess = { currentStep = ContactStep.LIST }
        )
    }
}

private enum class ContactStep {
    LIST, SEARCH, DETAIL, ADD_CONTACT, SEARCH_RESULT
}

data class ContactData(
    val id: String,
    val name: String,
    val email: String,
    val isFavorite: Boolean = false,
    val balanceLabel: String? = null,
    val sharedGroups: Int = 0,
)

/** Maps an API contact onto the row model, including the running balance. */
private fun Contact.toData() = ContactData(
    id = userId.value,
    name = displayName,
    email = email,
    isFavorite = isFavorite,
    balanceLabel = netBalance?.takeIf { !it.isZero }?.let { balance ->
        if (balance.isPositive) {
            "owes you ${balance.toPlainString()}"
        } else {
            "you owe ${balance.abs().toPlainString()}"
        }
    },
    sharedGroups = sharedGroupCount,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactListScreen(
    contacts: List<ContactData>,
    favorites: List<ContactData>,
    isLoading: Boolean,
    errorMessage: String?,
    onToggleFavorite: (ContactData) -> Unit,
    onSearchClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onContactClick: (ContactData) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("All Contacts", "Favorites")
    val visible = if (selectedTab == 1) favorites else contacts

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contacts", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddContactClick,
                containerColor = primaryLight,
                contentColor = Color.Black,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search Bar Placeholder (Screen 74)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable { onSearchClick() }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Search contact", color = Color.Gray)
                }
            }

            // Tabs
            Surface(
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF5F5F5)
            ) {
                Row {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) primaryLight else Color.Transparent)
                                .clickable { selectedTab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    errorMessage?.let { message ->
                        item {
                            Text(message, color = Color(0xFFD32F2F), fontSize = 14.sp)
                        }
                    }

                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = primaryLight)
                            }
                        }
                    }

                    if (!isLoading && visible.isEmpty()) {
                        item {
                            Text(
                                text = "No contacts yet. People you share a group with appear here.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }

                    items(visible, key = { it.id }) { contact ->
                        ContactListItem(
                            contact = contact,
                            onClick = { onContactClick(contact) },
                            onToggleFavorite = { onToggleFavorite(contact) }
                        )
                    }
                }
                
                // Alphabet index (Screen 75)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp, top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toList().forEach { char ->
                        Text(
                            text = char.toString(),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactListItem(
    contact: ContactData,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.LightGray))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                text = contact.balanceLabel ?: contact.email,
                fontSize = 12.sp,
                color = if (contact.balanceLabel != null) Color(0xFF2E7D32) else Color.Gray
            )
        }
        Icon(
            imageVector = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (contact.isFavorite) "Unfavourite" else "Favourite",
            tint = if (contact.isFavorite) primaryLight else Color.LightGray,
            modifier = Modifier.size(20.dp).clickable { onToggleFavorite() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContactScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<ContactData>,
    directoryResults: List<ContactData>,
    onNavigateBack: () -> Unit,
    onContactClick: (ContactData) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryLight.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Search contact", color = Color.Black) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black)
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Black)
                                    }
                                }
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = "Your contacts",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(results, key = { it.id }) { contact ->
                        ContactListItem(contact, onClick = { onContactClick(contact) })
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (results.isNotEmpty()) {
                        item {
                            Text(
                                text = "Your contacts",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(results, key = { it.id }) { contact ->
                            ContactListItem(contact, onClick = { onContactClick(contact) })
                        }
                    }

                    // Directory results are people not yet sharing a group; the server
                    // only answers once the query is long enough to not be enumerable.
                    if (directoryResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "On Splitfy",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(directoryResults, key = { "directory-" + it.id }) { contact ->
                            ContactListItem(contact, onClick = { onContactClick(contact) })
                        }
                    }

                    if (results.isEmpty() && directoryResults.isEmpty()) {
                        item {
                            Text(
                                text = "Nobody matches that yet",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailScreen(
    contact: ContactData,
    onNavigateBack: () -> Unit,
    onDeleteSuccess: () -> Unit,
    onToggleFavorite: () -> Unit = {},
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSuccessState by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contact", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(Icons.Default.StarBorder, contentDescription = "Favorite")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Request */ },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, primaryLight)
                ) {
                    Text("Request", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { /* Pay */ },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Pay", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.LightGray))
            Spacer(modifier = Modifier.height(16.dp))
            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(contact.email, color = Color.Gray, fontSize = 14.sp)
            Text("Splitify Account", color = Color.Gray, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            TextButton(onClick = { showDeleteDialog = true }) {
                Text("Delete Contact", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete Contact", fontWeight = FontWeight.Bold, color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                }
            },
            text = { 
                Text(
                    "Delete \"${contact.name}\" from your contacts?", 
                    textAlign = TextAlign.Center, 
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) 
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showDeleteDialog = false
                        showSuccessState = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Yes, Delete", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, primaryLight)
                ) {
                    Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showSuccessState) {
        ModalBottomSheet(
            onDismissRequest = onDeleteSuccess,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(80.dp).background(primaryLight, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Contact Deleted!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContactScreen(
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Add New Contact", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White,
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
                    onClick = onContinue,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Add Contact", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Select Mobile / Name", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Brooklyn Simmons", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedContainerColor = Color(0xFFF5F5F5),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Email", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("name@example.com", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedContainerColor = Color(0xFFF5F5F5),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultScreen(
    onNavigateBack: () -> Unit,
    onAddSuccess: () -> Unit
) {
    var showSuccessState by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Search Contact", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White,
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
                    onClick = { showSuccessState = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Add to Contact", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.LightGray))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Brooklyn Simmons", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("Ask them for the email they signed up with", color = Color.Gray, fontSize = 14.sp)
            Text("Splitify Account", color = Color.Gray, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("This contact is ready to split bills with you!", color = Color.Gray, fontSize = 14.sp)
        }
    }

    if (showSuccessState) {
        ModalBottomSheet(
            onDismissRequest = onAddSuccess,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(80.dp).background(primaryLight, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Contact Added Successfully!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
