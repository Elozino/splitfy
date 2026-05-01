package com.elozino.splitify.feature.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.elozino.splitify.feature.transaction.model.Transaction
import com.elozino.splitify.feature.transaction.model.TransactionType
import com.elozino.splitify.ui.theme.primaryLight
import splitify.composeapp.generated.resources.Res

@Composable
fun TransactionHistoryScreen(onNavigateBack: () -> Unit) {
    var selectedType by remember { mutableStateOf(TransactionType.ALL) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showReceipt by remember { mutableStateOf(false) }

    val transactions = remember {
        listOf(
            Transaction(
                id = "1",
                type = TransactionType.PAY,
                title = "Alfonzo Schuessler",
                subtitle = "Today | 09:41 AM",
                amount = "- $365.50",
                date = "Dec 20, 2023 - 09:41 AM",
                notes = "Dinner Delights Group Payments"
            ),
            Transaction(
                id = "2",
                type = TransactionType.REQUEST,
                title = "Maryland Winkles",
                subtitle = "Today | 09:39 AM",
                amount = "+ $642.50",
                date = "Dec 20, 2023 - 09:39 AM",
                notes = "Beyond Expense Explorer Group Payments"
            ),
            Transaction(
                id = "3",
                type = TransactionType.TOPUP,
                title = "Top Up",
                subtitle = "Today | 09:39 AM",
                amount = "+ $1,000.00",
                date = "Dec 20, 2023 - 09:39 AM",
                brandIcon = "drawable/ic_mastercard.svg",
                notes = "Top Up Splitify Balance."
            ),
            Transaction(
                id = "4",
                type = TransactionType.WITHDRAW,
                title = "Withdraw",
                subtitle = "Today | 09:38 AM",
                amount = "- $2,500.00",
                date = "Dec 20, 2023 - 09:38 AM",
                brandIcon = "drawable/ic_visa.svg",
                notes = "Withdraw to VISA Account."
            ),
            Transaction(
                id = "5",
                type = TransactionType.EXPENSE,
                title = "Japanese Grocery Shop",
                subtitle = "Today | 09:37 AM",
                amount = "- $200.00",
                date = "Dec 20, 2023 - 09:37 AM",
                notes = "Japanese Grocery Payments."
            )
        )
    }

    val filteredTransactions = if (selectedType == TransactionType.ALL) {
        transactions
    } else {
        transactions.filter { it.type == selectedType }
    }

    when {
        (showReceipt && selectedTransaction != null) -> {
            ReceiptScreen(
                transaction = selectedTransaction!!
            ) { showReceipt = false }
        }
        selectedTransaction != null -> {
            TransactionDetailScreen(
                transaction = selectedTransaction!!,
                onNavigateBack = { selectedTransaction = null },
                onViewReceipt = { showReceipt = true }
            )
        }
        else -> {
            Scaffold(
                topBar = {
                    TransactionHistoryTopBar(onNavigateBack)
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    TransactionFilters(
                        selectedType = selectedType,
                        onTypeSelected = { selectedType = it }
                    )
                    
                    TransactionList(
                        transactions = filteredTransactions,
                        onTransactionClick = { selectedTransaction = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionHistoryTopBar(onNavigateBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text("Transaction History", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = { /* Search */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }
    )
}

@Composable
private fun TransactionFilters(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit
) {
    val types = TransactionType.entries
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(types) { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { 
                    val label = when(type) {
                        TransactionType.TOPUP -> "Top Up"
                        else -> type.name.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    Text(label) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = primaryLight,
                    selectedLabelColor = Color.Black
                ),
                shape = RoundedCornerShape(100.dp),
                border = if (selectedType == type) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = primaryLight)
            )
        }
    }
}

@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Today",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(transactions) { transaction ->
            TransactionItem(transaction, onClick = { onTransactionClick(transaction) })
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.LightGray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (transaction.brandIcon != null) {
                AsyncImage(
                    model = Res.getUri(transaction.brandIcon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                // Default icon based on type
                Icon(
                    imageVector = when (transaction.type) {
                        TransactionType.PAY -> Icons.Default.ArrowUpward
                        TransactionType.REQUEST -> Icons.Default.ArrowDownward
                        TransactionType.TOPUP -> Icons.Default.AddCircle
                        TransactionType.WITHDRAW -> Icons.Default.RemoveCircle
                        TransactionType.EXPENSE -> Icons.Default.ShoppingCart
                        else -> Icons.Default.Payment
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when (transaction.type) {
                        TransactionType.PAY, TransactionType.EXPENSE, TransactionType.WITHDRAW -> Color.Red
                        TransactionType.REQUEST, TransactionType.TOPUP -> Color.Green
                        else -> Color.Gray
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(transaction.subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        
        Text(
            text = transaction.amount,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (transaction.amount.startsWith("-")) Color.Red else Color.Green
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailScreen(
    transaction: Transaction,
    onNavigateBack: () -> Unit,
    onViewReceipt: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(transaction.type.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryLight
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(primaryLight)
                    .padding(top = padding.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                         if (transaction.brandIcon != null) {
                            AsyncImage(
                                model = Res.getUri(transaction.brandIcon),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                             // Avatar placeholder
                             Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.LightGray))
                         }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = transaction.amount.replace("- ", "-").replace("+ ", "+"),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = transaction.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = if (transaction.type == TransactionType.PAY || transaction.type == TransactionType.REQUEST) "andrew.ainsley@gmail.com" else if (transaction.type == TransactionType.TOPUP) "Splitify Balance" else "**** **** **** 5567",
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                DetailRow("You have ${transaction.type.name.lowercase()}", transaction.amount.replace("- ", "").replace("+ ", ""))
                DetailRow(if (transaction.type == TransactionType.PAY || transaction.type == TransactionType.REQUEST) "To" else if (transaction.type == TransactionType.TOPUP) "From" else "Account", transaction.title)
                DetailRow("Date", transaction.date)
                DetailRow("Transaction ID", "2012202309${transaction.id}")
                DetailRow("Reference ID", "H375K7D${transaction.id}")
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Notes", color = Color.Gray, fontSize = 12.sp)
                Text(transaction.notes ?: "", fontSize = 14.sp)
                
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

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptScreen(
    transaction: Transaction,
    onNavigateBack: () -> Unit
) {
    var showShareSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Receipt", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { /* More */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Splitify Logo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = primaryLight
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Splitify", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Amount paid", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        text = transaction.amount.replace("- ", "").replace("+ ", "") + " USD",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    DetailRow("You have paid", transaction.amount.replace("- ", "").replace("+ ", ""))
                    DetailRow(if (transaction.type == TransactionType.PAY || transaction.type == TransactionType.REQUEST) "To" else if (transaction.type == TransactionType.TOPUP) "From" else "Account", transaction.title)
                    DetailRow("Email", "andrew.ainsley@gmail.com")
                    DetailRow("Date", transaction.date)
                    DetailRow("Transaction ID", "2012202309${transaction.id}")
                    DetailRow("Reference ID", "H375K7D${transaction.id}")
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Notes", color = Color.Gray, fontSize = 12.sp)
                        Text(transaction.notes ?: "", fontSize = 14.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(Color.LightGray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Splitify: Safe & Trusted Transaction.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Receipt downloaded successfully!",
                            withDismissAction = true,
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
            ) {
                Text("Download Receipt", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showShareSheet) {
        ShareBottomSheet(onDismiss = { showShareSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Share Receipt", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ShareOption("WhatsApp", Icons.AutoMirrored.Filled.Chat)
                ShareOption("Facebook", Icons.Default.Facebook)
                ShareOption("Instagram", Icons.Default.CameraAlt)
                ShareOption("Telegram", Icons.AutoMirrored.Filled.Send)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ShareOption(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.LightGray.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = name, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(name, fontSize = 12.sp)
    }
}
