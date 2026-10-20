package com.elozino.splitfy.screens.scan.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.api.dto.Contact
import com.elozino.splitfy.checkPermission
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.TransferKind
import com.elozino.splitfy.openAppSettings
import com.elozino.splitfy.PermissionStatus
import com.elozino.splitfy.PermissionType
import com.elozino.splitfy.rememberPermissionLauncher
import com.elozino.splitfy.ui.theme.onSurfaceDark
import com.elozino.splitfy.ui.theme.primaryLight
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.ui.camera.PeekabooCamera
import com.preat.peekaboo.ui.camera.rememberPeekabooCameraState
import org.koin.compose.viewmodel.koinViewModel
import com.elozino.splitfy.screens.transactions.shared.ScanStep
import com.elozino.splitfy.screens.transactions.shared.TransactionViewModel

@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val parsed = Money.parse(state.scanAmount, state.balance.currency)
    val clientRequestId = remember(state.scanRecipient) { "scan-pay-${'$'}{state.scanRecipient?.userId?.value.orEmpty()}" }

    LaunchedEffect(Unit) {
        viewModel.onCameraPermissionChange(checkPermission(PermissionType.CAMERA))
    }

    // The success screen appears only once the transfer has been recorded.
    LaunchedEffect(state.lastTransfer) {
        if (state.lastTransfer != null) viewModel.navigateScanTo(ScanStep.SUCCESS)
    }

    when (state.scanStep) {
        ScanStep.SCAN -> ScanQRCodeScreen(
            contacts = state.contacts,
            isPickerVisible = state.isRecipientPickerVisible,
            cameraPermissionStatus = state.cameraPermissionStatus,
            onPickerVisibilityChange = viewModel::setRecipientPickerVisible,
            onCameraPermissionChange = viewModel::onCameraPermissionChange,
            onNavigateBack = onNavigateBack,
            onRecipientPicked = viewModel::selectScanRecipient,
        )
        ScanStep.PROFILE -> ProfileScanScreen(
            recipient = state.scanRecipient,
            onNavigateBack = { viewModel.navigateScanTo(ScanStep.SCAN) },
            onPayClick = { viewModel.navigateScanTo(ScanStep.AMOUNT) },
        )
        ScanStep.AMOUNT -> ScanAmountScreen(
            amount = state.scanAmount,
            onAmountChange = viewModel::onScanAmountChange,
            onContinue = { viewModel.navigateScanTo(ScanStep.CONFIRMATION) },
            onNavigateBack = { viewModel.navigateScanTo(ScanStep.PROFILE) },
        )
        ScanStep.CONFIRMATION -> ScanConfirmationScreen(
            amount = state.scanAmount,
            recipient = state.scanRecipient,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onConfirm = {
                val payee = state.scanRecipient
                if (payee != null && parsed != null && parsed.isPositive) {
                    viewModel.send(
                        kind = TransferKind.PAYMENT,
                        counterparty = payee,
                        amount = parsed,
                        note = "Scan to pay",
                        clientRequestId = clientRequestId,
                    )
                }
            },
            onCancel = { viewModel.navigateScanTo(ScanStep.AMOUNT) },
            onNavigateBack = { viewModel.navigateScanTo(ScanStep.AMOUNT) },
        )
        ScanStep.SUCCESS -> ScanSuccessScreen(
            amount = state.scanAmount,
            transfer = state.lastTransfer,
            onDone = {
                viewModel.consumeLastTransfer()
                onNavigateBack()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanQRCodeScreen(
    contacts: List<Contact>,
    isPickerVisible: Boolean,
    cameraPermissionStatus: PermissionStatus,
    onPickerVisibilityChange: (Boolean) -> Unit,
    onCameraPermissionChange: (PermissionStatus) -> Unit,
    onNavigateBack: () -> Unit,
    onRecipientPicked: (Contact) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cameraLauncher = rememberPermissionLauncher(PermissionType.CAMERA) { status ->
        onCameraPermissionChange(status)
    }

    val cameraState = rememberPeekabooCameraState(
        onCapture = { byteArray ->
            // The camera frame is captured here, but turning it into a payload needs a
            // QR decoder, which this project does not depend on yet. Until one is
            // added the recipient is chosen explicitly rather than guessed.
            if (byteArray != null) onPickerVisibilityChange(true)
        }
    )

    val pickerLauncher = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        onResult = { byteArrays: List<ByteArray> ->
            if (byteArrays.isNotEmpty()) onPickerVisibilityChange(true)
        }
    )

    if (isPickerVisible) {
        RecipientPickerSheet(
            contacts = contacts,
            onDismiss = { onPickerVisibilityChange(false) },
            onPick = { contact ->
                onPickerVisibilityChange(false)
                onRecipientPicked(contact)
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Scan QR Code", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Live Camera Preview
            if (cameraPermissionStatus == PermissionStatus.GRANTED) {
                PeekabooCamera(
                    state = cameraState,
                    modifier = Modifier.fillMaxSize(),
                    permissionDeniedContent = {
                        onCameraPermissionChange(PermissionStatus.DENIED)
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoPhotography,
                                contentDescription = null,
                                modifier = Modifier.padding(20.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Camera Permission Required",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "To scan QR codes and make payments, splitfy needs access to your camera.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { cameraLauncher() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                        ) {
                            Text(
                                text = "Grant Permission",
                                color = onSurfaceDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { openAppSettings() }) {
                            Text("Open Settings", color = primaryLight)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    "Point the camera at the QR Code to scan",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(40.dp))
                
                // Scan Frame Overlay
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .border(2.dp, primaryLight, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Corner guides
                    Box(modifier = Modifier.fillMaxSize().padding(20.dp).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { cameraState.toggleCamera() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.FlipCameraIos, contentDescription = "Flip Camera", tint = Color.White)
                    }
                    
                    // Capture button
                    Box(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                            .size(70.dp)
                            .border(4.dp, primaryLight, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { cameraState.capture() }
                    )
                    
                    IconButton(
                        onClick = { pickerLauncher.launch() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Gallery", tint = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Confirms who the scanned code belongs to.
 *
 * Also the fallback while QR decoding is unimplemented: paying the wrong person is
 * unrecoverable, so the recipient is always confirmed against real contacts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientPickerSheet(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onPick: (Contact) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Who are you paying?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))

            if (contacts.isEmpty()) {
                Text(
                    "No contacts yet — join a group to pay someone.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            contacts.forEach { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(contact) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(contact.displayName, fontWeight = FontWeight.Bold)
                        Text(contact.email, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScanScreen(
    recipient: Contact?,
    onNavigateBack: () -> Unit,
    onPayClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.LightGray))
            Spacer(modifier = Modifier.height(16.dp))
            Text(recipient?.displayName.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(recipient?.email.orEmpty(), color = Color.Gray, fontSize = 14.sp)
            recipient?.netBalance?.takeIf { !it.isZero }?.let { balance ->
                Text(
                    text = if (balance.isPositive) {
                        "owes you ${'$'}{balance.toPlainString()}"
                    } else {
                        "you owe ${'$'}{balance.abs().toPlainString()}"
                    },
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "${'$'}{recipient?.sharedGroupCount ?: 0} shared group(s)",
                color = Color.Gray,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, primaryLight)
                ) {
                    Text("Request", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onPayClick,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Pay", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanAmountScreen(
    amount: String,
    onAmountChange: (String) -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Amount to Pay", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryLight)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(0.4f).background(primaryLight),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(amount, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Text("$", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                    }
                    Text("Available balance: $9,567.50", fontSize = 14.sp, color = Color.Black.copy(alpha = 0.7f))
                }
            }
            
            Column(
                modifier = Modifier.fillMaxWidth().weight(0.6f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                NumericKeypad(
                    onNumberClick = { num -> onAmountChange(if (amount == "0") num else amount + num) },
                    onDeleteClick = { if (amount.length > 1) onAmountChange(amount.dropLast(1)) else onAmountChange("0") },
                    onDotClick = { if (!amount.contains(".")) onAmountChange(amount + ".") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanConfirmationScreen(
    amount: String,
    recipient: Contact?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Pay Now", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Recipient", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(recipient?.displayName.orEmpty(), fontWeight = FontWeight.Bold)
                    Text(recipient?.email.orEmpty(), color = Color.Gray, fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Amount to Pay", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.LightGray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(amount, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Add Notes", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                color = Color.LightGray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Scan to pay", modifier = Modifier.padding(16.dp), color = Color.Gray)
            }

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(message, color = Color(0xFFD32F2F), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, primaryLight)
                ) {
                    Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting && recipient != null,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text(
                        text = if (isSubmitting) "Paying…" else "Pay Now",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanSuccessScreen(
    amount: String,
    transfer: com.elozino.splitfy.domain.model.Transfer?,
    onDone: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Box(modifier = Modifier.size(80.dp).background(primaryLight, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = transfer?.amount?.toPlainString() ?: amount,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Payment sent", color = Color.Gray)
            Text(transfer?.toName.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(24.dp))
            
            SuccessRow("You have paid", transfer?.amount?.toPlainString() ?: amount)
            SuccessRow("To", transfer?.toName.orEmpty())
            SuccessRow("Date", transfer?.createdAt?.toString()?.replace('T', ' ')?.take(16).orEmpty())
            SuccessRow("Reference", transfer?.id?.take(8).orEmpty())

            transfer?.note?.let { note ->
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Notes", color = Color.Gray, fontSize = 12.sp)
                    Text(note, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, primaryLight)
                ) {
                    Text("Download Receipt", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Share Receipt", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SuccessRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun NumericKeypad(onNumberClick: (String) -> Unit, onDeleteClick: () -> Unit, onDotClick: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "delete")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 4) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (j in 0 until 3) {
                    val key = keys[i * 3 + j]
                    Box(modifier = Modifier.size(72.dp).clickable { 
                        when(key) {
                            "delete" -> onDeleteClick()
                            "*" -> onDotClick()
                            else -> onNumberClick(key)
                        }
                    }, contentAlignment = Alignment.Center) {
                        if (key == "delete") Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                        else Text(if (key == "*") "." else key, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

