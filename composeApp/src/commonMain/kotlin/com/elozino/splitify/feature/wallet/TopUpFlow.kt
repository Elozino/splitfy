package com.elozino.splitify.feature.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.elozino.splitify.ui.theme.primaryLight
import splitify.composeapp.generated.resources.Res

@Composable
fun TopUpScreen(onNavigateBack: () -> Unit) {
    var currentStep by remember { mutableStateOf(TopUpStep.AMOUNT) }
    var amount by remember { mutableStateOf("1,000.00") }
    var selectedMethod by remember { mutableStateOf<PaymentMethod?>(null) }

    when (currentStep) {
        TopUpStep.AMOUNT -> TopUpAmountScreen(
            amount = amount,
            onAmountChange = { amount = it },
            onContinue = { currentStep = TopUpStep.METHOD },
            onNavigateBack = onNavigateBack
        )
        TopUpStep.METHOD -> TopUpMethodScreen(
            selectedMethod = selectedMethod,
            onMethodSelected = { selectedMethod = it },
            onContinue = { currentStep = TopUpStep.CONFIRMATION },
            onNavigateBack = { currentStep = TopUpStep.AMOUNT }
        )
        TopUpStep.CONFIRMATION -> TopUpConfirmationScreen(
            amount = amount,
            method = selectedMethod,
            onConfirm = { currentStep = TopUpStep.SUCCESS },
            onCancel = { currentStep = TopUpStep.METHOD },
            onNavigateBack = { currentStep = TopUpStep.METHOD }
        )
        TopUpStep.SUCCESS -> TopUpSuccessScreen(
            amount = amount,
            method = selectedMethod,
            onDone = onNavigateBack
        )
    }
}

private enum class TopUpStep {
    AMOUNT, METHOD, CONFIRMATION, SUCCESS
}

private data class PaymentMethod(
    val name: String,
    val icon: ImageVector,
    val details: String,
    val brandIcon: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopUpAmountScreen(
    amount: String,
    onAmountChange: (String) -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Amount to Top Up", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryLight,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = amount,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "$",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp),
                            color = Color.Black
                        )
                    }
                    Text(
                        text = "Available balance: $1,267.50",
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                NumericKeypad(
                    onNumberClick = { num ->
                        if (amount == "0.00") onAmountChange(num)
                        else onAmountChange(amount + num)
                    },
                    onDeleteClick = {
                        if (amount.isNotEmpty()) onAmountChange(amount.dropLast(1))
                    },
                    onDotClick = {
                        if (!amount.contains(".")) onAmountChange("$amount.")
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopUpMethodScreen(
    selectedMethod: PaymentMethod?,
    onMethodSelected: (PaymentMethod) -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val methods = remember {
        listOf(
            PaymentMethod("PayPal", Icons.Default.Payment, "paypal.com", "drawable/ic_paypal.svg"),
            PaymentMethod("Google Pay", Icons.Default.Payment, "google.com", "drawable/ic_google.svg"),
            PaymentMethod("Apple Pay", Icons.Default.Payment, "apple.com", "drawable/ic_apple.svg"),
            PaymentMethod("**** **** **** 4679", Icons.Default.CreditCard, "**** **** **** 4679", "drawable/ic_mastercard.svg"),
            PaymentMethod("**** **** **** 5567", Icons.Default.CreditCard, "**** **** **** 5567", "drawable/ic_visa.svg")
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Select Top Up Method", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Add new */ }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(24.dp)) {
                Button(
                    onClick = onContinue,
                    enabled = selectedMethod != null,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(methods) { method ->
                MethodItem(
                    method = method,
                    isSelected = selectedMethod == method,
                    onClick = { onMethodSelected(method) }
                )
            }
        }
    }
}

@Composable
private fun MethodItem(
    method: PaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, primaryLight) else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (method.brandIcon != null) {
                    AsyncImage(
                        model = Res.getUri(method.brandIcon),
                        contentDescription = method.name,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = method.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Black
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(method.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = primaryLight)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopUpConfirmationScreen(
    amount: String,
    method: PaymentMethod?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Top Up", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text("Amount to Top Up", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.LightGray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "$$amount",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Top Up Method", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            if (method != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (method.brandIcon != null) {
                            AsyncImage(
                                model = Res.getUri(method.brandIcon),
                                contentDescription = method.name,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(method.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(method.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            "Change",
                            color = primaryLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onCancel() }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Add Notes", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                color = Color.LightGray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Top Up Splitify Balance.",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Black
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                    Text("Confirm Top Up", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TopUpSuccessScreen(
    amount: String,
    method: PaymentMethod?,
    onDone: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDone) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier.size(80.dp).background(primaryLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Black)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(text = "$$amount", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(text = "You top up to Splitify Balance", color = Color.Gray)
            Text(text = "Andrew Ainsley", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(24.dp))
            
            ReceiptRow("You top up", "$$amount")
            ReceiptRow("From", method?.details ?: "")
            ReceiptRow("Payment method", method?.name ?: "")
            ReceiptRow("Date", "Dec 20, 2023 - 09:39 AM")
            ReceiptRow("Transaction ID", "201220230939")
            ReceiptRow("Reference ID", "Z8H2K3F7")
            
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Notes", color = Color.Gray, fontSize = 12.sp)
                Text("Top Up Splitify Balance.", fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, primaryLight)
                ) {
                    Text("Download Receipt", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Share Receipt", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onDotClick: () -> Unit
) {
    val keys = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "*", "0", "delete"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (j in 0 until 3) {
                    val key = keys[i * 3 + j]
                    KeypadButton(
                        key = key,
                        onClick = {
                            when (key) {
                                "delete" -> onDeleteClick()
                                "*" -> onDotClick()
                                else -> onNumberClick(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(key: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (key == "delete") {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = if (key == "*") "." else key,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
