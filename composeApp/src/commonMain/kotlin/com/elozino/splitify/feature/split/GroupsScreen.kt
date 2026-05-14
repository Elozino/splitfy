package com.elozino.splitify.feature.split

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.ui.theme.primaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onNavigateToNewGroup: () -> Unit
) {
    // Current groups list logic
    val groups = remember { 
        mutableStateListOf(
            GroupItemData("Trip to Bali", "4 members", "$120.50", true),
            GroupItemData("House Rent", "3 members", "$450.00", false),
            GroupItemData("Dinner Party", "6 members", "$85.20", true),
            GroupItemData("Gym Subscription", "2 members", "$30.00", false)
        )
    }

    GroupsListScreen(
        groups = groups,
        onCreateGroupClick = onNavigateToNewGroup
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsListScreen(
    groups: List<GroupItemData>,
    onCreateGroupClick: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Groups", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGroupClick,
                containerColor = primaryLight,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Group")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyGroupsState(onCreateGroupClick, padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups) { group ->
                    GroupItem(group)
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
            .padding(padding)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewGroupScreen(
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Trip") }
    var selectedCurrency by remember { mutableStateOf("USD") }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    val currencies = listOf("USD", "EUR", "NGN")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("New Group", fontWeight = FontWeight.Bold) },
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
                    .padding(start = 24.dp, end = 24.dp, top= 16.dp, bottom = 32.dp),
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
                    Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(24.dp)
        ) {
            item {
                // Group Image Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5F5))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { /* TODO: Pick Image */ },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Upload Cover Image", color = Color.Gray, fontSize = 14.sp)
                    }
                    // Edit icon overlay - normally visible when image is set
                    Box(
                       modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(32.dp).background(primaryLight, CircleShape),
                       contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Text("Title", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("Title", color = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Text("Description", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 100.dp),
                    placeholder = { Text("Description", color = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Text("Currency", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { showCurrencyMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F5F5)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedCurrency, color = Color.Black)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    DropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = { showCurrencyMenu = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                    ) {
                        currencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text(currency) },
                                onClick = {
                                    selectedCurrency = currency
                                    showCurrencyMenu = false
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    categories.forEach { (name, emoji) ->
                        val isSelected = selectedCategory == name
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = name },
                            label = { Text("$name $emoji") },
                            shape = RoundedCornerShape(100.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White,
                                selectedContainerColor = primaryLight,
                                labelColor = Color.Black,
                                selectedLabelColor = Color.Black
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.LightGray.copy(alpha = 0.5f),
                                selectedBorderColor = primaryLight,
                                borderWidth = 1.dp,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectParticipantsScreen(
    onNavigateBack: () -> Unit,
    onFinish: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val contacts = listOf(
        Contact("Alexia Hershey", "alexia_hershey@gmail.com"),
        Contact("Alfonzo Schuessler", "alfonzo.schuessler@gmail.com"),
        Contact("Augustina Midgett", "augustina.midgett@gmail.com"),
        Contact("Charlotte Hanlin", "charlotte.hanlin@gmail.com"),
        Contact("Darron Kulikowski", "darron.kulikowski@gmail.com"),
        Contact("Florencio Dorrance", "florencio.dorrance@gmail.com"),
        Contact("Geoffrey Mott", "geoffrey.mott@gmail.com")
    )
    val selectedContacts = remember { mutableStateListOf<Contact>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Select Participants", fontWeight = FontWeight.Bold) },
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
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
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
                    onClick = onFinish,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
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
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        placeholder = { Text("Search contact", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF5F5F5),
                            focusedContainerColor = Color(0xFFF5F5F5),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                if (selectedContacts.isNotEmpty()) {
                    item {
                        // Selected horizontal list
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(selectedContacts) { contact ->
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.LightGray))
                                    Box(
                                        modifier = Modifier.size(18.dp).background(Color.Black, CircleShape).border(1.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
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
                        color = Color(0xFFF5F5F5)
                    ) {
                        Row {
                            val tabs = listOf("All Contacts", "Favorites")
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
                    Spacer(modifier = Modifier.height(24.dp))
                }

                items(contacts) { contact ->
                    val isSelected = selectedContacts.contains(contact)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .clickable { 
                                if (isSelected) selectedContacts.remove(contact)
                                else selectedContacts.add(contact)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.LightGray))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(contact.email, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
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
                    Text(text = char.toString(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private data class Contact(val name: String, val email: String)

data class GroupItemData(
    val name: String,
    val members: String,
    val balance: String,
    val isOwed: Boolean
)

@Composable
fun GroupItem(data: GroupItemData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
