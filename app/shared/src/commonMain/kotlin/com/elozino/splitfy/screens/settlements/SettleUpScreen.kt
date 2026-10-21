package com.elozino.splitfy.screens.settlements

import com.elozino.splitfy.ui.components.LoadErrorState
import com.elozino.splitfy.ui.components.SkeletonText
import com.elozino.splitfy.ui.components.SkeletonListItem
import com.elozino.splitfy.ui.components.SkeletonList
import com.elozino.splitfy.ui.components.SkeletonBlock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SuggestedTransfer
import com.elozino.splitfy.ui.theme.primaryLight
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val Owed = Color(0xFF4CAF50)
private val Owes = Color(0xFFE57373)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: SettleUpViewModel = koinViewModel { parametersOf(groupId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Errors inside the sheet are shown in the sheet; everything else goes here.
    LaunchedEffect(state.errorMessage, state.draft) {
        val message = state.errorMessage
        if (message != null && state.draft == null) {
            snackbar.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settle up", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.White,
    ) { padding ->
        when {
            state.isLoading && state.group == null -> SkeletonList(
                modifier = Modifier.padding(padding),
                count = 5,
                spacing = 18.dp,
            ) { index ->
                if (index == 0) {
                    SkeletonText(0.35f, height = 16.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonListItem(modifier = Modifier.weight(1f), avatarSize = 40.dp, trailing = false)
                        Spacer(Modifier.width(12.dp))
                        SkeletonBlock(Modifier.width(110.dp).height(36.dp), RoundedCornerShape(100.dp))
                    }
                }
            }

            state.loadError != null && state.group == null ->
                LoadErrorState(state.loadError!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))

            state.isAllSettled -> AllSettled(Modifier.padding(padding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                section("Waiting for you to confirm", state.awaitingYourConfirmation) { settlement ->
                    PendingRow(
                        title = "${state.nameOf(settlement.fromUserId)} says they paid you",
                        settlement = settlement,
                        isBusy = settlement.id.value in state.busySettlementIds,
                        primaryLabel = "Confirm",
                        onPrimary = { viewModel.confirm(settlement) },
                        secondaryLabel = "Not received",
                        onSecondary = { viewModel.cancel(settlement) },
                    )
                }

                section("Your payments", state.yourTransfers) { transfer ->
                    val youPay = transfer.from.value == state.currentUserId
                    TransferRow(
                        title = if (youPay) {
                            "You owe ${state.nameOf(transfer.to)}"
                        } else {
                            "${state.nameOf(transfer.from)} owes you"
                        },
                        transfer = transfer,
                        youPay = youPay,
                        actionLabel = if (youPay) "Mark as paid" else "Record payment",
                        onAction = { viewModel.startPayment(transfer) },
                    )
                }

                section("Waiting for them to confirm", state.awaitingTheirConfirmation) { settlement ->
                    PendingRow(
                        title = "You paid ${state.nameOf(settlement.toUserId)}",
                        settlement = settlement,
                        isBusy = settlement.id.value in state.busySettlementIds,
                        secondaryLabel = "Cancel",
                        onSecondary = { viewModel.cancel(settlement) },
                    )
                }

                section("Between other members", state.otherTransfers) { transfer ->
                    TransferRow(
                        title = "${state.nameOf(transfer.from)} owes ${state.nameOf(transfer.to)}",
                        transfer = transfer,
                        youPay = false,
                    )
                }

                section("History", state.history) { settlement ->
                    HistoryRow(
                        title = "${state.nameOf(settlement.fromUserId)} paid " +
                            state.nameOf(settlement.toUserId, startsSentence = false),
                        settlement = settlement,
                    )
                }
            }
        }
    }

    state.draft?.let { draft ->
        RecordPaymentSheet(
            title = if (draft.isReceipt(state.currentUserId)) {
                "Payment from ${state.nameOf(draft.from)}"
            } else {
                "Payment to ${state.nameOf(draft.to)}"
            },
            hint = if (draft.isReceipt(state.currentUserId)) {
                "This is recorded as received straight away."
            } else {
                "${state.nameOf(draft.to)} will be asked to confirm they received it."
            },
            draft = draft,
            currencyCode = state.currency.code,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onAmountChange = viewModel::onAmountChange,
            onNoteChange = viewModel::onNoteChange,
            onSubmit = viewModel::submitPayment,
            onDismiss = viewModel::dismissPayment,
        )
    }
}

private fun <T> LazyListScope.section(title: String, rows: List<T>, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    item {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(rows) { row(it) }
    item { HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f)) }
}

@Composable
private fun AllSettled(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🎉", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("Everyone is settled up", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("Nobody owes anything in this group.", color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
private fun Avatar() {
    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
}

@Composable
private fun TransferRow(
    title: String,
    transfer: SuggestedTransfer,
    youPay: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar()
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                "${transfer.amount.toPlainString()} ${transfer.amount.currency.code}",
                color = if (youPay) Owes else Owed,
                fontSize = 12.sp,
            )
        }
        if (actionLabel != null) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(actionLabel, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PendingRow(
    title: String,
    settlement: Settlement,
    isBusy: Boolean,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar()
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    "${settlement.amount.toPlainString()} ${settlement.amount.currency.code}" +
                        (settlement.note?.let { " · $it" } ?: ""),
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isBusy) {
                CircularProgressIndicator(color = primaryLight, modifier = Modifier.size(24.dp))
                return@Row
            }
            if (primaryLabel != null) {
                Button(
                    onClick = onPrimary,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight),
                ) {
                    Text(primaryLabel, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = onSecondary,
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, Color.LightGray),
            ) {
                Text(secondaryLabel, color = Color.Black, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HistoryRow(title: String, settlement: Settlement) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar()
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(settlement.settledAt.toString().take(10), color = Color.Gray, fontSize = 12.sp)
        }
        Text(
            "${settlement.amount.toPlainString()} ${settlement.amount.currency.code}",
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentSheet(
    title: String,
    hint: String,
    draft: PaymentDraft,
    currencyCode: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(hint, color = Color.Gray, fontSize = 13.sp)

            OutlinedTextField(
                value = draft.amountText,
                onValueChange = onAmountChange,
                label = { Text("Amount ($currencyCode)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNoteChange,
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g. Bank transfer", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            errorMessage?.let { Text(it, color = Color.Red, fontSize = 13.sp) }

            Button(
                onClick = onSubmit,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Text("Record payment", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
