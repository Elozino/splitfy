package com.elozino.splitify.feature.split.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
fun GroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAddExpense: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExpenseDetail: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Expenses", "Balances", "Totals", "Group info")
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Trip to Japan 🇯🇵", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Group") },
                                onClick = { 
                                    showMenu = false
                                    onNavigateToSettings() 
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Leave Group") },
                                onClick = { showMenu = false },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Group", color = Color.Red) },
                                onClick = { showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { /* TODO: Settle Up */ },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, primaryLight)
                    ) {
                        Text("Settle up", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onNavigateToAddExpense,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                    ) {
                        Text("Add expense", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Group Header Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.LightGray)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(36.dp)
                        .background(primaryLight, CircleShape)
                        .clickable { /* Edit Cover */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                }
            }

            // Tabs
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                edgePadding = 24.dp,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab, true),
                        color = primaryLight
                    )
                },
                divider = { HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        selectedContentColor = Color.Black,
                        unselectedContentColor = Color.Gray
                    )
                }
            }

            when (selectedTab) {
                0 -> ExpensesList(onExpenseClick = { onNavigateToExpenseDetail("1") })
                1 -> BalancesList()
                2 -> TotalsList()
                3 -> GroupInfoList()
            }
        }
    }
}

@Composable
private fun ExpensesList(onExpenseClick: () -> Unit) {
    val expenses = listOf(
        ExpenseItemData("Japanese Grocery Shop", "You paid $200.00", "$200.00", "Today", "🍱"),
        ExpenseItemData("Dinner Delights", "Andrew paid $120.00", "$120.00", "Yesterday", "🥘"),
        ExpenseItemData("Taxi Ride", "You paid $30.00", "$30.00", "Yesterday", "🚕"),
        ExpenseItemData("Museum Tickets", "Maryland paid $85.00", "$85.00", "Dec 18, 2023", "🎟️")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(expenses) { expense ->
            ExpenseItem(expense, onClick = onExpenseClick)
        }
    }
}

@Composable
private fun ExpenseItem(data: ExpenseItemData, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.LightGray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(data.emoji, fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(data.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(data.subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(data.amount, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Text(data.date, color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BalancesList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Horizontal Balance Bars (Screen 61)
        BalanceBar("Andrew Ainsley (You)", 0.8f, Color(0xFF4CAF50), "+ $1,267.50")
        BalanceBar("Charlotte Hanlin", 0.6f, Color(0xFFE57373), "- $642.50")
        BalanceBar("Darron Kulikowski", 0.4f, Color(0xFFE57373), "- $365.50")
        BalanceBar("Kristin Watson", 0.7f, Color(0xFF4CAF50), "+ $259.50")
        BalanceBar("Joseph Thomas", 0.5f, Color(0xFF4CAF50), "+ $110.00")
        BalanceBar("Maryland Winkles", 0.2f, Color(0xFFE57373), "- $0.00")

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(24.dp))

        // Balance Items with Request Button (Screen 62)
        BalanceItem("Maryland Winkles", "owes you $642.50", isOwed = true, showRequest = true)
        BalanceItem("Alfonzo Schuessler", "owes you $365.50", isOwed = true)
        BalanceItem("Darron Kulikowski", "owes you $259.50", isOwed = true)
    }
}

@Composable
private fun BalanceBar(name: String, progress: Float, color: Color, amount: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontSize = 12.sp, color = Color.Gray)
            Text(amount, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color.LightGray.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun BalanceItem(name: String, details: String, isOwed: Boolean, showRequest: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold)
            Text(details, color = if (isOwed) Color(0xFF4CAF50) else Color.Red, fontSize = 12.sp)
        }
        if (showRequest) {
            TextButton(
                onClick = { /* TODO */ },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Request", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TotalsList() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Dec 2023", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCard("Total Group Spending", "$1,048.50", Modifier.weight(1f))
            SummaryCard("Your Share", "$1,234.15", Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        SummaryCard("Your Payments", "$648.50", Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.weight(1f))
        
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFE8F5E9)
        ) {
            DetailRow(
                "Current balance", 
                "You are owed $1,267.50", 
                isHighlight = true,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF5F5F5),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GroupInfoList() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Trip to Japan 🇯🇵", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Holidaying with college friends in Japan", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(24.dp))
        DetailRow("Currency", "USD")
        DetailRow("Category", "Trip ✈️")
        
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Group Members", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = primaryLight)
                Text("Add", color = primaryLight, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        val members = listOf("Andrew Ainsley (You)", "Charlotte Hanlin", "Darron Kulikowski")
        members.forEach { name ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
                Spacer(modifier = Modifier.width(16.dp))
                Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String, 
    value: String, 
    isHighlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (isHighlight) Color.Black else Color.Gray, fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium)
        Text(value, fontWeight = FontWeight.Bold, color = if (isHighlight) Color(0xFF4CAF50) else Color.Black)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    expenseId: String,
    onNavigateBack: () -> Unit,
    onViewReceipt: () -> Unit
) {
    // Mock data using expenseId
    val amount = if (expenseId == "1") "$200.00" else "$0.00"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Japanese Grocery Shop", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(primaryLight),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🍱", fontSize = 40.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("- $200.00", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Paid by You", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Dec 20, 2023 - 09:37 AM", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.7f))
                    Text("japan_grocery@yourdomain.com", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.7f))
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                DetailRow("You have paid", "$200.00")
                DetailRow("To", "Japanese Grocery Shop")
                DetailRow("Email", "japan_grocery@yourdomain.com")
                DetailRow("Date", "Dec 20, 2023 - 09:37 AM")
                DetailRow("Transaction ID", "201220230937")
                DetailRow("Reference ID", "H34K2L93")
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Notes", color = Color.Gray, fontSize = 12.sp)
                Text("Japanese Grocery Payments.", fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(2.dp, Color.White, CircleShape)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = onViewReceipt,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("View Receipt", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class ExpenseItemData(
    val title: String,
    val subtitle: String,
    val amount: String,
    val date: String,
    val emoji: String
)
