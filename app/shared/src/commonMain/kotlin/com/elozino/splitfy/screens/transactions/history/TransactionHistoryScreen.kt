package com.elozino.splitfy.screens.transactions.history

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
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
import coil3.compose.AsyncImage
import com.elozino.splitfy.ui.generated.resources.UiRes
import com.elozino.splitfy.Transaction
import com.elozino.splitfy.TransactionType
import com.elozino.splitfy.ui.theme.primaryLight
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.elozino.splitfy.screens.expenses.add.label
import com.elozino.splitfy.screens.groups.detail.main.DetailRow
import com.elozino.splitfy.screens.transactions.shared.TransactionViewModel

@Composable
fun TransactionHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedType = state.filter

    // The statement is the wallet ledger; filtering happens server-side so paging
    // stays correct rather than filtering only the page already in memory.
    val filteredTransactions = state.entries.map(Transaction::from)

    when {
        (state.isReceiptVisible && state.selectedTransaction != null) -> {
            ReceiptScreen(
                transaction = state.selectedTransaction!!,
                isShareSheetVisible = state.isReceiptShareVisible,
                onShareClick = { viewModel.setReceiptShareVisible(true) },
                onShareDismiss = { viewModel.setReceiptShareVisible(false) },
            ) { viewModel.setReceiptVisible(false) }
        }
        state.selectedTransaction != null -> {
            TransactionDetailScreen(
                transaction = state.selectedTransaction!!,
                onNavigateBack = viewModel::dismissTransaction,
                onViewReceipt = { viewModel.setReceiptVisible(true) },
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
                        onTypeSelected = viewModel::onFilterChange
                    )

                    when {
                        state.isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = primaryLight)
                        }

                        filteredTransactions.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions yet",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }

                        else -> TransactionList(
                            transactions = filteredTransactions,
                            onTransactionClick = viewModel::selectTransaction,
                            onLoadMore = viewModel::loadMore,
                            hasMore = state.hasMore
                        )
                    }
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
    onTransactionClick: (Transaction) -> Unit,
    onLoadMore: () -> Unit = {},
    hasMore: Boolean = false
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
        items(transactions, key = { it.id }) { transaction ->
            TransactionItem(transaction, onClick = { onTransactionClick(transaction) })
        }

        if (hasMore) {
            item {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load more", color = primaryLight, fontWeight = FontWeight.Bold)
                }
            }
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
                    model = UiRes.getUri(transaction.brandIcon),
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
                                model = UiRes.getUri(transaction.brandIcon),
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
                        text = transaction.notes ?: transaction.title,
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
    isShareSheetVisible: Boolean,
    onShareClick: () -> Unit,
    onShareDismiss: () -> Unit,
    onNavigateBack: () -> Unit
) {
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
                    IconButton(onClick = onShareClick) {
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
                    // splitfy Logo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = primaryLight
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("splitfy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                    DetailRow("Balance after", transaction.balanceAfter)
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
                        Text("splitfy: Safe & Trusted Transaction.", color = Color.Gray, fontSize = 12.sp)
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

    if (isShareSheetVisible) {
        ShareBottomSheet(onDismiss = onShareDismiss)
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

