package com.elozino.splitfy.screens.groups.detail.expense

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import com.elozino.splitfy.ui.components.SkeletonText
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.elozino.splitfy.screens.expenses.add.label
import com.elozino.splitfy.screens.groups.detail.main.DetailRow
import com.elozino.splitfy.screens.expenses.emoji
import com.elozino.splitfy.screens.groups.detail.shared.GroupDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    groupId: String,
    expenseId: String,
    onNavigateBack: () -> Unit,
    viewModel: GroupDetailViewModel = koinViewModel { parametersOf(groupId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expense = state.expenses.firstOrNull { it.id.value == expenseId }
    val payer = state.group?.members?.firstOrNull { it.userId == expense?.paidBy }
    val paidByYou = expense?.paidBy?.value == state.currentUserId
    val yourShare = expense?.shares?.firstOrNull { it.userId.value == state.currentUserId }?.amount
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }

    // The link is signed for minutes, so it is opened as soon as it arrives and never kept.
    LaunchedEffect(state.receiptLinkToOpen) {
        state.receiptLinkToOpen?.let { url ->
            viewModel.consumeReceiptLink()
            runCatching { uriHandler.openUri(url) }
                .onFailure { snackbar.showSnackbar("Couldn't open the receipt") }
        }
    }
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
                    if (expense == null) SkeletonBlock(Modifier.width(140.dp).height(20.dp), RoundedCornerShape(6.dp))
                    else Text(expense.description, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
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
                    .background(AppTheme.colors.brand),
                contentAlignment = Alignment.Center
            ) {
                if (expense == null) Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBlock(Modifier.size(80.dp), CircleShape, onAccent = true)
                    SkeletonBlock(Modifier.width(180.dp).height(36.dp), onAccent = true)
                    SkeletonBlock(Modifier.width(130.dp).height(16.dp), RoundedCornerShape(6.dp), onAccent = true)
                    SkeletonBlock(Modifier.width(100.dp).height(12.dp), RoundedCornerShape(6.dp), onAccent = true)
                } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(expense?.category?.emoji() ?: "🧾", fontSize = 40.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = expense?.total?.toPlainString().orEmpty(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Paid by " + if (paidByYou) "You" else payer?.displayName.orEmpty(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = expense?.incurredAt?.toString()?.replace('T', ' ')?.take(16).orEmpty(),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onBrand.copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.group?.name.orEmpty(),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onBrand.copy(alpha = 0.7f)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
            ) {
                if (expense == null) {
                    // Label/value pairs, the shape of the detail rows below.
                    SkeletonList(count = 6, contentPadding = PaddingValues(0.dp), spacing = 22.dp) { index ->
                        Row {
                            SkeletonText(0.25f + (index % 2) * 0.1f, height = 14.dp, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(48.dp))
                            SkeletonBlock(Modifier.width(80.dp).height(14.dp), RoundedCornerShape(6.dp))
                        }
                    }
                    return@Column
                }
                DetailRow("Total", expense.total.toPlainString())
                DetailRow("Your share", yourShare?.toPlainString() ?: "-")
                DetailRow("Split", expense?.splitMethod?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "-")
                DetailRow("Group", state.group?.name.orEmpty())
                DetailRow("Date", expense?.incurredAt?.toString()?.take(10).orEmpty())
                DetailRow("Expense ID", expenseId.take(8))

                expense?.notes?.let { notes ->
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Notes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(notes, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Split between", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                expense?.shares.orEmpty().forEach { share ->
                    val member = state.group?.members?.firstOrNull { it.userId == share.userId }
                    DetailRow(
                        label = if (share.userId.value == state.currentUserId) {
                            "You"
                        } else {
                            member?.displayName ?: "Someone"
                        },
                        value = share.amount.toPlainString()
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                val isOpening = state.openingReceiptFor == expenseId
                Button(
                    onClick = { viewModel.openReceipt(expenseId) },
                    enabled = expense?.receiptUrl != null && !isOpening,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
                ) {
                    Text(
                        text = when {
                            isOpening -> "Opening…"
                            expense?.receiptUrl != null -> "View Receipt"
                            else -> "No receipt attached"
                        },
                        color = AppTheme.colors.onBrand,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

