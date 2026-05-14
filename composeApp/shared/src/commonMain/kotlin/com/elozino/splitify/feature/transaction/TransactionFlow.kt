package com.elozino.splitify.feature.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elozino.splitify.feature.transaction.model.TransactionType
import com.elozino.splitify.feature.transaction.model.TransactionUser

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
    onNavigateBack: () -> Unit
) {
    // In a real-world scenario, these would likely be managed in a ViewModel.
    var selectedUser by remember { mutableStateOf<TransactionUser?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    // Mock data - should be provided by a ViewModel
    val users = remember {
        listOf(
            TransactionUser("Andrew Ainsley (You)", "andrew.ainsley@gmail.com", "$365.50"),
            TransactionUser("Alfonzo Schuessler", "alfonzo.schuessler@gmail.com", "$365.50"),
            TransactionUser("Maryland Winkles", "maryland.winkles@gmail.com", "$642.50"),
            TransactionUser("Darron Kulikowski", "darron.kulikowski@gmail.com", "$208.40")
        )
    }

    Scaffold(
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
            }
        )
    }

    if (showConfirmation && selectedUser != null) {
        TransactionConfirmationSheet(
            type = type,
            user = selectedUser!!,
            onConfirm = {
                showConfirmation = false
                showSuccess = true
            },
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
                onNavigateBack()
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
    onUserSelected: (TransactionUser) -> Unit
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
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(users) { user ->
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
                    text = "owes you",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionConfirmationSheet(
    type: TransactionType,
    user: TransactionUser,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = sheetState,
    ) {
        TransactionConfirmationContent(
            type = type,
            user = user,
            onConfirm = onConfirm,
            onCancel = onDismiss
        )
    }
}

@Composable
private fun TransactionConfirmationContent(
    type: TransactionType,
    user: TransactionUser,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (type == TransactionType.PAY) "Pay" else "Request",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        TransactionAmountSummary(user.amount)

        Spacer(modifier = Modifier.height(24.dp))
        
        UserDetailsRow(user)

        Spacer(modifier = Modifier.height(24.dp))
        
        TransactionNotesField(note = "Dinner Delights Group Payments")

        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (type == TransactionType.PAY) "Pay Now" else "Request Now",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TransactionAmountSummary(amount: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Amount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your available balance: $1,267.50",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun UserDetailsRow(user: TransactionUser) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
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
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun TransactionNotesField(note: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Notes (optional)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = note,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SuccessFlow(
    type: TransactionType,
    user: TransactionUser,
    onDismiss: () -> Unit
) {
    if (type == TransactionType.PAY) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            PaymentSuccessScreen(user = user, onDismiss = onDismiss)
        }
    } else {
        RequestSuccessSheet(onDismiss = onDismiss)
    }
}

@Composable
private fun PaymentSuccessScreen(
    user: TransactionUser,
    onDismiss: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            SuccessCircleIcon()
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = user.amount,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Paid to ${user.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))
            
            PaymentReceiptDetails(user)
            
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Dinner Delights Group Payments",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            ReceiptActions()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestSuccessSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SuccessCircleIcon()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Request Has Been Sent!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SuccessCircleIcon() {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun PaymentReceiptDetails(user: TransactionUser) {
    DetailRow("You have paid", user.amount)
    DetailRow("To", user.name)
    DetailRow("Email", user.email)
    DetailRow("Date", user.date)
    DetailRow("Transaction ID", "201220230941")
    DetailRow("Reference ID", "H375K7D9")
}

@Composable
private fun ReceiptActions() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(
            onClick = {},
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(25.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text("Download Receipt", color = MaterialTheme.colorScheme.onSurface)
        }
        Button(
            onClick = {},
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Share Receipt", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
