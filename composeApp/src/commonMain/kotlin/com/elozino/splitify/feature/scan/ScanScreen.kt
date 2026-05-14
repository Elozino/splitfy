package com.elozino.splitify.feature.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.PermissionStatus
import com.elozino.splitify.PermissionType
import com.elozino.splitify.checkPermission
import com.elozino.splitify.openAppSettings
import com.elozino.splitify.rememberPermissionLauncher
import com.elozino.splitify.ui.theme.onSurfaceDark
import com.elozino.splitify.ui.theme.primaryLight
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.ui.camera.PeekabooCamera
import com.preat.peekaboo.ui.camera.rememberPeekabooCameraState

@Composable
fun ScanScreen(onNavigateBack: () -> Unit) {
    var currentStep by remember { mutableStateOf(ScanStep.SCAN) }
    var amount by remember { mutableStateOf("200.00") }

    when (currentStep) {
        ScanStep.SCAN -> ScanQRCodeScreen(
            onNavigateBack = onNavigateBack,
            onScanSuccess = { currentStep = ScanStep.PROFILE }
        )
        ScanStep.PROFILE -> ProfileScanScreen(
            onNavigateBack = { currentStep = ScanStep.SCAN },
            onPayClick = { currentStep = ScanStep.AMOUNT }
        )
        ScanStep.AMOUNT -> ScanAmountScreen(
            amount = amount,
            onAmountChange = { amount = it },
            onContinue = { currentStep = ScanStep.CONFIRMATION },
            onNavigateBack = { currentStep = ScanStep.PROFILE }
        )
        ScanStep.CONFIRMATION -> ScanConfirmationScreen(
            amount = amount,
            onConfirm = { currentStep = ScanStep.SUCCESS },
            onCancel = { currentStep = ScanStep.AMOUNT },
            onNavigateBack = { currentStep = ScanStep.AMOUNT }
        )
        ScanStep.SUCCESS -> ScanSuccessScreen(
            amount = amount,
            onDone = onNavigateBack
        )
    }
}

private enum class ScanStep {
    SCAN, PROFILE, AMOUNT, CONFIRMATION, SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanQRCodeScreen(onNavigateBack: () -> Unit, onScanSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cameraPermissionStatus by remember { mutableStateOf(checkPermission(PermissionType.CAMERA)) }
    
    val cameraLauncher = rememberPermissionLauncher(PermissionType.CAMERA) { status ->
        cameraPermissionStatus = status
    }

    val cameraState = rememberPeekabooCameraState(
        onCapture = { byteArray ->
            if (byteArray != null) {
                onScanSuccess()
            }
        }
    )

    val pickerLauncher = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        onResult = { byteArrays: List<ByteArray> ->
            if (byteArrays.isNotEmpty()) {
                onScanSuccess()
            }
        }
    )

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
                        cameraPermissionStatus = PermissionStatus.DENIED
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
                            text = "To scan QR codes and make payments, Splitify needs access to your camera.",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScanScreen(onNavigateBack: () -> Unit, onPayClick: () -> Unit) {
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
            Text("Japanese Grocery Shop", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("japan_grocery@yourdomain.com", color = Color.Gray, fontSize = 14.sp)
            Text("Splitify Merchant", color = Color.Gray, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("This account is not in your contacts yet", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {},
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, primaryLight)
            ) {
                Text("Add to My Contact", color = Color.Black)
            }
            
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
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onNavigateBack: () -> Unit
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
                    Text("Japanese Grocery Shop", fontWeight = FontWeight.Bold)
                    Text("japan_grocery@yourdomain.com", color = Color.Gray, fontSize = 12.sp)
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
                Text("$$amount", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Add Notes", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                color = Color.LightGray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Japanese Grocery Payments.", modifier = Modifier.padding(16.dp))
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
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Pay Now", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScanSuccessScreen(amount: String, onDone: () -> Unit) {
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
            Text("$$amount", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Paid to Japanese Grocery Shop", color = Color.Gray)
            Text("japan_grocery@yourdomain.com", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(24.dp))
            
            SuccessRow("You have paid", "$$amount")
            SuccessRow("To", "Japanese Grocery Shop")
            SuccessRow("Email", "japan_grocery@yourdomain.com")
            SuccessRow("Date", "Dec 20, 2023 - 09:37 AM")
            SuccessRow("Transaction ID", "201220230937")
            SuccessRow("Reference ID", "H34K2L93")
            
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Notes", color = Color.Gray, fontSize = 12.sp)
                Text("Japanese Grocery Payments.", fontSize = 14.sp)
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
