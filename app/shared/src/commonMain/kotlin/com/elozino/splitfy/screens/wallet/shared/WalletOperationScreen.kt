package com.elozino.splitfy.screens.wallet.shared

import com.elozino.splitfy.ui.components.SkeletonListItem
import com.elozino.splitfy.ui.components.SkeletonList
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.domain.model.PaymentMethod
import com.elozino.splitfy.domain.model.PaymentMethodType
import com.elozino.splitfy.ui.theme.primaryLight
import org.koin.compose.viewmodel.koinViewModel

/**
 * Top up and withdraw are the same four steps with a different direction, so they
 * share one implementation rather than two near-identical copies that drift.
 */
@Composable
internal fun WalletOperationFlow(
    operation: WalletOperation,
    viewModel: WalletViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Stable for the life of the screen: a retry after a timeout must not move money
    // twice. The server treats a repeat of this key as the same operation.
    val clientRequestId = remember { "${operation.name.lowercase()}-${state.hashCode()}" }

    val title = if (operation == WalletOperation.TOP_UP) "Top Up" else "Withdraw"

    when (state.step) {
        WalletStep.AMOUNT -> AmountStep(
            title = "Amount to $title",
            state = state,
            operation = operation,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onContinue = { viewModel.navigateTo(WalletStep.METHOD) },
            onNavigateBack = onNavigateBack,
        )

        WalletStep.METHOD -> MethodStep(
            title = "Select $title Method",
            state = state,
            onMethodSelected = viewModel::onMethodSelected,
            onContinue = { viewModel.navigateTo(WalletStep.CONFIRM) },
            onNavigateBack = { viewModel.navigateTo(WalletStep.AMOUNT) },
        )

        WalletStep.CONFIRM -> ConfirmStep(
            title = title,
            state = state,
            onConfirm = { viewModel.submit(operation, clientRequestId) },
            onNavigateBack = { viewModel.navigateTo(WalletStep.METHOD) },
        )

        WalletStep.SUCCESS -> SuccessStep(
            title = title,
            state = state,
            onDone = {
                viewModel.reset()
                onNavigateBack()
            },
        )
    }

    // The success screen appears only once the server has confirmed the movement.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountStep(
    title: String,
    state: WalletState,
    operation: WalletOperation,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val overdrawn = state.exceedsBalance(operation)

    Scaffold(
        topBar = { FlowTopBar(title, onNavigateBack, containerColor = primaryLight) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.amountInput.ifEmpty { "0" },
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = state.currency.code,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp),
                            color = Color.Black
                        )
                    }
                    Text(
                        text = "Available balance: ${state.balance.toPlainString()}",
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                    if (overdrawn) {
                        Text(
                            text = "That's more than your balance",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Button(
                    onClick = onContinue,
                    enabled = state.canContinue && !overdrawn,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryLight,
                        disabledContainerColor = primaryLight.copy(alpha = 0.5f)
                    )
                ) {
                    Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
                NumericKeypad(onDigit = onDigit, onBackspace = onBackspace)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodStep(
    title: String,
    state: WalletState,
    onMethodSelected: (String) -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = { FlowTopBar(title, onNavigateBack) },
        containerColor = Color.White,
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().padding(24.dp)) {
                Button(
                    onClick = onContinue,
                    enabled = state.selectedMethod != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryLight,
                        disabledContainerColor = primaryLight.copy(alpha = 0.5f)
                    )
                ) {
                    Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            SkeletonList(modifier = Modifier.padding(padding), count = 4) {
                SkeletonListItem(avatarSize = 40.dp, trailing = false)
            }
            return@Scaffold
        }

        if (state.paymentMethods.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No payment methods yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Add a card or bank account from Account → Payment methods.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.paymentMethods, key = { it.id }) { method ->
                MethodItem(
                    method = method,
                    isSelected = state.selectedMethod?.id == method.id,
                    onClick = { onMethodSelected(method.id) }
                )
            }
        }
    }
}

@Composable
private fun MethodItem(method: PaymentMethod, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) {
            BorderStroke(2.dp, primaryLight)
        } else {
            BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
        },
        color = Color.White
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = method.type.icon(),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(method.maskedLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (method.isDefault) {
                    Text("Default", color = Color.Gray, fontSize = 12.sp)
                }
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = primaryLight)
            )
        }
    }
}

private fun PaymentMethodType.icon(): ImageVector = when (this) {
    PaymentMethodType.CARD -> Icons.Default.CreditCard
    PaymentMethodType.BANK_ACCOUNT -> Icons.Default.AccountBalance
    else -> Icons.Default.Payment
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmStep(
    title: String,
    state: WalletState,
    onConfirm: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = { FlowTopBar("Confirm $title", onNavigateBack) },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = state.amount?.toPlainString().orEmpty() + " " + state.currency.code,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.selectedMethod?.maskedLabel.orEmpty(), color = Color.Gray, fontSize = 14.sp)

            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(24.dp))
                Text(message, color = Color(0xFFD32F2F), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onConfirm,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
            ) {
                Text(
                    text = if (state.isSubmitting) "Working…" else "Confirm",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SuccessStep(title: String, state: WalletState, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(primaryLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("$title successful", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "New balance: ${state.balance.toPlainString()} ${state.currency.code}",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
        ) {
            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    containerColor: Color = Color.White,
) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = Color.Black,
            navigationIconContentColor = Color.Black
        )
    )
}

@Composable
private fun NumericKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable { if (key == "⌫") onBackspace() else onDigit(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = Color.Black
                            )
                        } else {
                            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

