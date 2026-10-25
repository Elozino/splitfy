package com.elozino.splitfy.screens.groups.detail.main

import com.elozino.splitfy.components.UserAvatar
import com.elozino.splitfy.screens.expenses.emoji
import com.elozino.splitfy.screens.groups.detail.shared.GroupActions
import com.elozino.splitfy.ui.components.RefreshableBox
import com.elozino.splitfy.ui.components.LoadErrorState
import com.elozino.splitfy.ui.components.shimmer
import com.elozino.splitfy.ui.components.SkeletonText
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
import coil3.compose.AsyncImage
import com.elozino.splitfy.domain.model.Expense
import com.preat.peekaboo.image.picker.ResizeOptions
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import androidx.compose.ui.layout.ContentScale
import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.ui.components.SearchField
import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.koin.compose.viewmodel.koinViewModel
import com.elozino.splitfy.adaptive.LocalWindowLayout
import com.elozino.splitfy.adaptive.TabletopBottomBar
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
    onNavigateToInvite: () -> Unit,
    onNavigateToExpenseDetail: (String) -> Unit,
    viewModel: GroupDetailViewModel = koinViewModel { parametersOf(groupId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-read on entry so an expense added elsewhere is reflected here.
    LaunchedEffect(groupId) { viewModel.load() }

    val tabs = listOf("Expenses", "Balances", "Totals", "Group info")
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val coverPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        resizeOptions = ResizeOptions(width = 2048, height = 2048, compressionQuality = 0.9),
        onResult = { images -> images.firstOrNull()?.let(viewModel::onCoverImage) },
    )

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (isSearching) {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = "Search expenses",
                            onClose = { isSearching = false },
                        )
                    } else {
                        if (state.group == null && state.isLoading) {
                            SkeletonBlock(Modifier.width(140.dp).height(20.dp), RoundedCornerShape(6.dp))
                        } else {
                            Text(
                                text = state.group?.name ?: "Group",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = {
                            isSearching = true
                            viewModel.onTabSelected(0)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    Box {
                        IconButton(onClick = { viewModel.setOverflowMenuVisible(true) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = state.isOverflowMenuVisible,
                            onDismissRequest = { viewModel.setOverflowMenuVisible(false) },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            if (state.group?.isArchived == false) DropdownMenuItem(
                                text = { Text("Invite by link") },
                                onClick = {
                                    viewModel.setOverflowMenuVisible(false)
                                    onNavigateToInvite()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            )
                            // Editing, leaving and archiving all live in settings, behind confirmations.
                            DropdownMenuItem(
                                text = { Text("Group settings") },
                                onClick = {
                                    viewModel.setOverflowMenuVisible(false)
                                    onNavigateToSettings()
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Below the fold when the device stands half open; unchanged otherwise.
            TabletopBottomBar {
                GroupActionsBar(
                    actions = state.actions,
                    onAddExpense = onNavigateToAddExpense,
                    onSettleUp = onNavigateToSettleUp,
                )
            }
        }
    ) { padding ->
        RefreshableBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, indicatorPadding = padding) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Group Header Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // A quarter of a short window (a phone on its side, or the top half of a
                        // foldable standing half open), 200dp otherwise.
                        .height(minOf(200.dp, LocalWindowLayout.current.usableHeight / 4))
                        .then(if (state.isFirstLoad) Modifier.shimmer() else Modifier.background(MaterialTheme.colorScheme.surfaceVariant))
                ) {
                    state.group?.coverImageUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Cover photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (state.isUploadingCover) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.surface)
                        }
                    }
                    if (state.canEditCover) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(36.dp)
                                .background(AppTheme.colors.brand, CircleShape)
                                .clickable(enabled = !state.isUploadingCover) { coverPicker.launch() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Change cover photo", modifier = Modifier.size(18.dp), tint = AppTheme.colors.onBrand)
                        }
                    }
                }

                // Tabs
                SecondaryScrollableTabRow(
                    selectedTabIndex = state.selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 24.dp,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(state.selectedTab, true),
                            color = AppTheme.colors.brand
                        )
                    },
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
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
                            selectedContentColor = MaterialTheme.colorScheme.onSurface,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val loadError = state.loadError
                if (loadError != null && state.group == null) {
                    LoadErrorState(loadError, onRetry = viewModel::load)
                } else when (state.selectedTab) {
                    0 -> ExpensesList(
                        state = state,
                        query = query,
                        onExpenseClick = { expenseId -> onNavigateToExpenseDetail(expenseId) },
                        onLoadMore = viewModel::loadMore,
                    )
                    1 -> BalancesList(state, onSettleUp = onNavigateToSettleUp)
                    2 -> TotalsList(state)
                    3 -> GroupInfoList(
                        state = state,
                        onAddMember = { viewModel.setAddMemberDialogVisible(true) },
                        onRemoveMember = viewModel::removeMember,
                    )
                }
            }
        }
    }

    if (state.isAddMemberDialogVisible) {
        AddMemberDialog(
            state = state,
            onEmailChange = viewModel::onMemberEmailChange,
            onConfirm = viewModel::addMember,
            onDismiss = { viewModel.setAddMemberDialogVisible(false) },
        )
    }
}

/**
 * The bottom actions, from where the group's money stands. Once everyone is square
 * there is nothing to settle, so the bar celebrates instead, while ongoing groups
 * (a flat's bills) keep a quiet way to add the next expense.
 */
@Composable
private fun GroupActionsBar(
    actions: GroupActions,
    onAddExpense: () -> Unit,
    onSettleUp: () -> Unit,
) {
    if (actions == GroupActions.None) return
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        val barModifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
        when (actions) {
            GroupActions.SettledUp -> Column(barModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Everyone's settled up 🎉", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Nobody owes anything in this group.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                TextButton(onClick = onAddExpense) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AppTheme.colors.onBrand, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add an expense", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
                }
            }
            else -> Row(barModifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (actions == GroupActions.AddAndSettle) {
                    OutlinedButton(
                        onClick = onSettleUp,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, AppTheme.colors.brand)
                    ) {
                        Text("Settle up", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = onAddExpense,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
                ) {
                    Text("Add expense", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    state: GroupDetailState,
    onEmailChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Add a member", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("They need a Splitfy account with this email.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.memberEmail,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    isError = state.memberError != null,
                    supportingText = state.memberError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isUpdatingMembers) {
                Text("Add", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

@Composable
private fun ExpensesList(
    state: GroupDetailState,
    query: String,
    onExpenseClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (state.isLoading && state.expenses.isEmpty()) {
        SkeletonList(count = 6, spacing = 16.dp)
        return
    }

    val needle = query.trim()
    val expenses = if (needle.isEmpty()) state.expenses else state.expenses.filter {
        it.description.contains(needle, ignoreCase = true) || it.notes?.contains(needle, ignoreCase = true) == true
    }

    if (needle.isNotEmpty() && expenses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No expenses match \"$needle\"", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        items(expenses, key = { it.id.value }) { expense ->
            ExpenseItem(
                data = expense.toItem(state),
                onClick = { onExpenseClick(expense.id.value) }
            )
        }

        // Search only covers loaded pages, so keep offering more while filtering.
        if (state.hasMore) {
            item {
                if (state.isLoadingMore) {
                    CircularProgressIndicator(color = AppTheme.colors.brand, modifier = Modifier.size(24.dp))
                } else {
                    TextButton(onClick = onLoadMore) {
                        Text("Load more", color = AppTheme.colors.brand, fontWeight = FontWeight.Bold)
                    }
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(data.emoji, fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(data.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(data.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(data.amount, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(data.date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BalancesList(state: GroupDetailState, onSettleUp: () -> Unit) {
    if (state.isFirstLoad) {
        // A label and a bar per member, as the real balances are drawn.
        SkeletonList(count = 4, spacing = 18.dp) { index ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonText(0.3f + (index % 3) * 0.1f, height = 12.dp)
                SkeletonBlock(Modifier.fillMaxWidth(0.4f + (index % 4) * 0.15f).height(8.dp), RoundedCornerShape(4.dp))
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // One bar per member, scaled against the largest position in the group.
        val names = state.group?.members.orEmpty().associate { it.userId to it.displayName }
        val avatars = state.group?.members.orEmpty().associate { it.userId to it.avatarUrl }
        val largest = state.balances.maxOfOrNull { kotlin.math.abs(it.net.amount) } ?: 0L

        state.balances.forEach { balance ->
            val isYou = balance.userId.value == state.currentUserId
            val label = (names[balance.userId] ?: "Someone") + if (isYou) " (You)" else ""
            BalanceBar(
                name = label,
                progress = if (largest == 0L) 0f else kotlin.math.abs(balance.net.amount) / largest.toFloat(),
                color = if (balance.net.isNegative) AppTheme.colors.negative else AppTheme.colors.positive,
                amount = (if (balance.net.isNegative) "- " else "+ ") + balance.net.abs().toPlainString()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(24.dp))

        // One row per outstanding transfer, taken from the shared debt simplifier.
        if (state.suggestedTransfers.isEmpty()) {
            Text("Everyone is settled up 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        state.suggestedTransfers.forEach { transfer ->
            val youArePayer = transfer.from.value == state.currentUserId
            val youArePayee = transfer.to.value == state.currentUserId
            val amount = transfer.amount.toPlainString()
            val counterparty = if (youArePayer) transfer.to else transfer.from
            BalanceItem(
                name = names[counterparty] ?: "Someone",
                avatarUrl = avatars[counterparty],
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
            Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            trackColor = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun BalanceItem(
    name: String,
    avatarUrl: String?,
    details: String,
    isOwed: Boolean,
    showSettle: Boolean = false,
    onSettle: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(avatarUrl, name)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold)
            Text(details, color = if (isOwed) AppTheme.colors.positive else AppTheme.colors.negative, fontSize = 12.sp)
        }
        if (showSettle) {
            TextButton(
                onClick = onSettle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settle", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TotalsList(state: GroupDetailState) {
    if (state.isFirstLoad) {
        SkeletonList(count = 1) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonText(0.25f, height = 16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SkeletonBlock(Modifier.weight(1f).height(84.dp), RoundedCornerShape(16.dp))
                    SkeletonBlock(Modifier.weight(1f).height(84.dp), RoundedCornerShape(16.dp))
                }
                SkeletonBlock(Modifier.fillMaxWidth().height(84.dp), RoundedCornerShape(16.dp))
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("All time", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCard("Total Group Spending", state.totalSpend.toPlainString(), Modifier.weight(1f))
            SummaryCard("Your Share", state.yourShare.toPlainString(), Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        SummaryCard("Your Payments", state.yourPayments.toPlainString(), Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.weight(1f))
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = AppTheme.colors.positive.copy(alpha = 0.15f)
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
        color = AppTheme.colors.field,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GroupInfoList(
    state: GroupDetailState,
    onAddMember: () -> Unit,
    onRemoveMember: (String) -> Unit,
) {
    if (state.isFirstLoad) {
        SkeletonList(count = 5, spacing = 16.dp) { index ->
            if (index == 0) SkeletonText(0.5f, height = 20.dp) else SkeletonListItem(avatarSize = 40.dp, trailing = false)
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(state.group?.name ?: "Group", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        state.group?.description?.let { description ->
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        DetailRow("Currency", state.currency.code)
        DetailRow("Category", state.group?.category?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "-")
        
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Group Members", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (state.canManageMembers) {
                TextButton(onClick = onAddMember) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AppTheme.colors.brand)
                    Text("Add", color = AppTheme.colors.brand, fontWeight = FontWeight.Bold)
                }
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
                UserAvatar(member.avatarUrl, member.displayName)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Bold)
                    Text(
                        member.role.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (state.canRemove(member)) {
                    var isMenuVisible by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { isMenuVisible = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Member options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(
                            expanded = isMenuVisible,
                            onDismissRequest = { isMenuVisible = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Remove from group", color = AppTheme.colors.negative) },
                                onClick = {
                                    isMenuVisible = false
                                    onRemoveMember(member.userId.value)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = AppTheme.colors.negative)
                                }
                            )
                        }
                    }
                }
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
        Text(label, color = if (isHighlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium)
        Text(value, fontWeight = FontWeight.Bold, color = if (isHighlight) AppTheme.colors.positive else MaterialTheme.colorScheme.onSurface)
    }
}


