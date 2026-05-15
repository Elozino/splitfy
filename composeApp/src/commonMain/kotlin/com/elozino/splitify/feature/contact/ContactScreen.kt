package com.elozino.splitify.feature.contact

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.ui.theme.primaryLight

@Composable
fun ContactScreen(onVisibilityChanged: (Boolean) -> Unit) {
    var currentStep by remember { mutableStateOf(ContactStep.LIST) }
    var selectedContact by remember { mutableStateOf<ContactData?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(currentStep) {
        onVisibilityChanged(currentStep == ContactStep.LIST)
    }

    when (currentStep) {
        ContactStep.LIST -> ContactListScreen(
            onSearchClick = { currentStep = ContactStep.SEARCH },
            onAddContactClick = { currentStep = ContactStep.ADD_CONTACT },
            onContactClick = { 
                selectedContact = it
                currentStep = ContactStep.DETAIL 
            }
        )
        ContactStep.SEARCH -> SearchContactScreen(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onNavigateBack = { currentStep = ContactStep.LIST },
            onContactClick = {
                selectedContact = it
                currentStep = ContactStep.DETAIL
            }
        )
        ContactStep.DETAIL -> ContactDetailScreen(
            contact = selectedContact!!,
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
    val name: String,
    val email: String,
    val isFavorite: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactListScreen(
    onSearchClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onContactClick: (ContactData) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("All Contacts", "Favorites")
    
    val contacts = remember {
        listOf(
            ContactData("Alexia Hershey", "alexia_hershey@gmail.com"),
            ContactData("Alfonzo Schuessler", "alfonzo.schuessler@gmail.com"),
            ContactData("Augustina Midgett", "augustina.midgett@gmail.com"),
            ContactData("Charlotte Hanlin", "charlotte_hanlin@gmail.com"),
            ContactData("Darron Kulikowski", "darron.kulikowski@gmail.com"),
            ContactData("Florencio Dorrance", "florencio.dorrance@gmail.com"),
            ContactData("Geoffrey Mott", "geoffrey.mott@gmail.com")
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contacts", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddContactClick,
                containerColor = primaryLight,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
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
                    items(contacts) { contact ->
                        ContactListItem(contact, onClick = { onContactClick(contact) })
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
private fun ContactListItem(contact: ContactData, onClick: () -> Unit) {
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
            Text(contact.email, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(
            imageVector = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = null,
            tint = if (contact.isFavorite) primaryLight else Color.LightGray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContactScreen(
    query: String,
    onQueryChange: (String) -> Unit,
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
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            if (query.isEmpty()) {
                Text("Recent searches", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
                // Mock recent searches
                val recent = listOf("Theresa Webb", "Geoffrey Mott", "Alexia Hershey")
                recent.forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                // Mock results
                val results = listOf(
                    ContactData("Charlotte Hanlin", "charlotte_hanlin@gmail.com"),
                    ContactData("Geoffrey Mott", "geoffrey.mott@gmail.com")
                ).filter { it.name.contains(query, ignoreCase = true) }
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(results) { contact ->
                        ContactListItem(contact, onClick = { onContactClick(contact) })
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
    onDeleteSuccess: () -> Unit
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
                    IconButton(onClick = { /* TODO: Favorite */ }) {
                        Icon(Icons.Default.StarBorder, contentDescription = "Favorite")
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
                placeholder = { Text("brooklyn_simmons@gmail.com", color = Color.Gray) },
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
            Text("brooklyn_simmons@gmail.com", color = Color.Gray, fontSize = 14.sp)
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
