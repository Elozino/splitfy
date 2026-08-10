package com.elozino.splitify.feature.transaction

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.feature.transaction.model.TransactionType
import com.elozino.splitify.feature.transaction.model.TransactionUser
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitify.feature.transaction.presentation.SuccessFlow
import com.elozino.splitify.feature.transaction.presentation.TransactionConfirmationSheet
import com.elozino.splitify.feature.transaction.presentation.TransactionViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Public entry point for the Pay flow.
 */
@Composable
fun PayScreen(onNavigateBack: () -> Unit) {
    TransactionScreen(type = TransactionType.PAY, onNavigateBack = onNavigateBack)
}

/**
 * Public entry point for the Request flow.
 */
@Composable
fun RequestScreen(onNavigateBack: () -> Unit) {
    TransactionScreen(type = TransactionType.REQUEST, onNavigateBack = onNavigateBack)
}

/**
 * Main entry point for the Transaction Flow (Pay/Request).
 * 
 * Refactored for:
 * 1. Material 3 Theme Consistency.
 * 2. Componentization & Separation of concerns.
 * 3. Scalable State Management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionScreen(
    type: TransactionType,
    onNavigateBack: () -> Unit,
    viewModel: TransactionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedUser by remember { mutableStateOf<TransactionUser?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    // Paying settles what you owe; requesting chases what you are owed. Contacts who
    // are square with you are not actionable and are left out.
    val users = state.contacts.mapNotNull(TransactionUser::from).filter { user ->
        if (type == TransactionType.REQUEST) user.owesYou else !user.owesYou
    }

    // Stable across recomposition so a retried tap cannot send twice.
    val clientRequestId = remember(selectedUser) {
        "transfer-${'$'}{type.name}-${'$'}{selectedUser?.userId?.value.orEmpty()}"
    }

    // The success sheet appears only after the server has recorded the transfer.
    LaunchedEffect(state.lastTransfer) {
        if (state.lastTransfer != null) {
            showConfirmation = false
            showSuccess = true
        }
    }

    val blurBackground = showConfirmation || showSuccess

    Scaffold(
        modifier = Modifier.blur(if (blurBackground) 16.dp else 0.dp),
        topBar = {
            TransactionTopBar(
                title = if (type == TransactionType.PAY) "Pay" else "Request",
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        TransactionContent(
            modifier = Modifier.padding(padding),
            users = users,
            type = type,
            onUserSelected = {
                selectedUser = it
                showConfirmation = true
            },
            isLoading = state.isLoading,
            errorMessage = state.errorMessage
        )
    }

    if (showConfirmation && selectedUser != null) {
        TransactionConfirmationSheet(
            type = type,
            user = selectedUser!!,
            onConfirm = {
                selectedUser?.let { user ->
                    viewModel.send(
                        kind = type.toTransferKind(),
                        counterparty = state.contacts.first { it.userId == user.userId },
                        amount = user.balance.abs(),
                        note = null,
                        clientRequestId = clientRequestId,
                    )
                }
            },
            isSubmitting = state.isSubmitting,
            onDismiss = {
                showConfirmation = false
                selectedUser = null
            }
        )
    }

    if (showSuccess && selectedUser != null) {
        SuccessFlow(
            type = type,
            user = selectedUser!!,
            onDismiss = {
                showSuccess = false
                selectedUser = null
                viewModel.consumeLastTransfer()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = { 
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold 
            ) 
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
private fun TransactionContent(
    modifier: Modifier = Modifier,
    users: List<TransactionUser>,
    type: TransactionType,
    onUserSelected: (TransactionUser) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Today",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (users.isEmpty()) {
            Text(
                text = if (type == TransactionType.REQUEST) {
                    "Nobody owes you right now."
                } else {
                    "You don't owe anyone right now."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(users, key = { it.userId.value }) { user ->
                UserListItem(
                    user = user,
                    actionLabel = if (type == TransactionType.PAY) "Pay" else "Request",
                    onClick = { onUserSelected(user) }
                )
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: TransactionUser,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (user.owesYou) "owes you" else "you owe",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = user.amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 12.dp)
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
