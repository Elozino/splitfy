package com.elozino.splitfy.screens.groups.detail.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.ui.theme.primaryLight
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.elozino.splitfy.screens.expenses.add.label
import com.elozino.splitfy.screens.groups.detail.shared.ExpenseItemData
import com.elozino.splitfy.screens.groups.detail.shared.GroupDetailState
import com.elozino.splitfy.screens.groups.detail.shared.GroupDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAddExpense: () -> Unit,
    onNavigateToSettleUp: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExpenseDetail: (String) -> Unit,
    viewModel: GroupDetailViewModel = koinViewModel { parametersOf(groupId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-read on entry so an expense added elsewhere is reflected here.
    LaunchedEffect(groupId) { viewModel.load() }

    val tabs = listOf("Expenses", "Balances", "Totals", "Group info")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.group?.name ?: "Group",
                        fontWeight = FontWeight.Bold
                    )
                },
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
                        IconButton(onClick = { viewModel.setOverflowMenuVisible(true) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = state.isOverflowMenuVisible,
                            onDismissRequest = { viewModel.setOverflowMenuVisible(false) },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Group") },
                                onClick = { 
                                    viewModel.setOverflowMenuVisible(false)
                                    onNavigateToSettings() 
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Leave Group") },
                                onClick = { viewModel.setOverflowMenuVisible(false) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Group", color = Color.Red) },
                                onClick = { viewModel.setOverflowMenuVisible(false) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White,
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
                        onClick = onNavigateToSettleUp,
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
                selectedTabIndex = state.selectedTab,
                containerColor = Color.White,
                edgePadding = 24.dp,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(state.selectedTab, true),
                        color = primaryLight
                    )
                },
                divider = { HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.onTabSelected(index) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        selectedContentColor = Color.Black,
                        unselectedContentColor = Color.Gray
                    )
                }
            }

            when (state.selectedTab) {
                0 -> ExpensesList(
                    state = state,
                    onExpenseClick = { expenseId -> onNavigateToExpenseDetail(expenseId) }
                )
                1 -> BalancesList(state, onSettleUp = onNavigateToSettleUp)
                2 -> TotalsList(state)
                3 -> GroupInfoList(state)
            }
        }
    }
}

@Composable
private fun ExpensesList(state: GroupDetailState, onExpenseClick: (String) -> Unit) {
    if (state.isLoading && state.expenses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = primaryLight)
        }
        return
    }

    if (state.expenses.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No expenses yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Add the first one and we'll keep everyone's share up to date.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(state.expenses, key = { it.id.value }) { expense ->
            ExpenseItem(
                data = expense.toItem(state),
                onClick = { onExpenseClick(expense.id.value) }
            )
        }

        if (state.hasMore) {
            item {
                TextButton(onClick = { /* paging is driven by the caller below */ }) {
                    Text("Load more", color = primaryLight, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Turns an API expense into the row model, naming the payer relative to the viewer. */
private fun Expense.toItem(state: GroupDetailState): ExpenseItemData {
    val payer = state.group?.members?.firstOrNull { it.userId == paidBy }
    val payerLabel = when {
        paidBy.value == state.currentUserId -> "You"
        payer != null -> payer.displayName.substringBefore(' ')
        else -> "Someone"
    }
    return ExpenseItemData(
        title = description,
        subtitle = "$payerLabel paid ${total.toPlainString()} ${total.currency.code}",
        amount = total.toPlainString(),
        date = incurredAt.toString().take(10),
        emoji = category.emoji()
    )
}

internal fun ExpenseCategory.emoji(): String = when (this) {
    ExpenseCategory.FOOD -> "🍱"
    ExpenseCategory.TRANSPORT -> "🚕"
    ExpenseCategory.ACCOMMODATION -> "🏨"
    ExpenseCategory.GROCERIES -> "🛒"
    ExpenseCategory.ENTERTAINMENT -> "🎟️"
    ExpenseCategory.UTILITIES -> "💡"
    ExpenseCategory.SHOPPING -> "🛍️"
    ExpenseCategory.HEALTH -> "🩺"
    ExpenseCategory.OTHER -> "🧾"
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
private fun BalancesList(state: GroupDetailState, onSettleUp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // One bar per member, scaled against the largest position in the group.
        val names = state.group?.members.orEmpty().associate { it.userId to it.displayName }
        val largest = state.balances.maxOfOrNull { kotlin.math.abs(it.net.amount) } ?: 0L

        state.balances.forEach { balance ->
            val isYou = balance.userId.value == state.currentUserId
            val label = (names[balance.userId] ?: "Someone") + if (isYou) " (You)" else ""
            BalanceBar(
                name = label,
                progress = if (largest == 0L) 0f else kotlin.math.abs(balance.net.amount) / largest.toFloat(),
                color = if (balance.net.isNegative) Color(0xFFE57373) else Color(0xFF4CAF50),
                amount = (if (balance.net.isNegative) "- " else "+ ") + balance.net.abs().toPlainString()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(24.dp))

        // One row per outstanding transfer, taken from the shared debt simplifier.
        if (state.suggestedTransfers.isEmpty()) {
            Text("Everyone is settled up 🎉", color = Color.Gray, fontSize = 14.sp)
        }
        state.suggestedTransfers.forEach { transfer ->
            val youArePayer = transfer.from.value == state.currentUserId
            val youArePayee = transfer.to.value == state.currentUserId
            val amount = transfer.amount.toPlainString()
            BalanceItem(
                name = when {
                    youArePayer -> names[transfer.to] ?: "Someone"
                    youArePayee -> names[transfer.from] ?: "Someone"
                    else -> names[transfer.from] ?: "Someone"
                },
                details = when {
                    youArePayer -> "you owe $amount"
                    youArePayee -> "owes you $amount"
                    else -> "owes ${names[transfer.to] ?: "someone"} $amount"
                },
                isOwed = !youArePayer,
                showSettle = youArePayer || youArePayee,
                onSettle = onSettleUp,
            )
        }
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
private fun BalanceItem(
    name: String,
    details: String,
    isOwed: Boolean,
    showSettle: Boolean = false,
    onSettle: () -> Unit = {},
) {
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
        if (showSettle) {
            TextButton(
                onClick = onSettle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settle", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TotalsList(state: GroupDetailState) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Dec 2023", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCard("Total Group Spending", state.totalSpend.toPlainString(), Modifier.weight(1f))
            SummaryCard("Your Share", state.yourShare.toPlainString(), Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        SummaryCard("Your Payments", state.yourPayments.toPlainString(), Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.weight(1f))
        
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFE8F5E9)
        ) {
            val net = state.yourBalance(state.currentUserId) ?: Money.zero(state.currency)
            DetailRow(
                "Current balance",
                when {
                    net.isZero -> "You are all square"
                    net.isPositive -> "You are owed ${net.toPlainString()}"
                    else -> "You owe ${net.abs().toPlainString()}"
                },
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
private fun GroupInfoList(state: GroupDetailState) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(state.group?.name ?: "Group", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        state.group?.description?.let { description ->
            Text(description, color = Color.Gray, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        DetailRow("Currency", state.currency.code)
        DetailRow("Category", state.group?.category?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "-")
        
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Group Members", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = primaryLight)
                Text("Add", color = primaryLight, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        state.group?.members.orEmpty().forEach { member ->
            val label = if (member.userId.value == state.currentUserId) {
                "${member.displayName} (You)"
            } else {
                member.displayName
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Bold)
                    Text(
                        member.role.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}

@Composable
internal fun DetailRow(
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


