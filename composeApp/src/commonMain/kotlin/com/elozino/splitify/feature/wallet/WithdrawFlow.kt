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
import com.elozino.splitify.ui.theme.primaryLight
import coil3.compose.AsyncImage
import splitify.composeapp.generated.resources.Res

@Composable
fun WithdrawScreen(onNavigateBack: () -> Unit) {
    var currentStep by remember { mutableStateOf(WithdrawStep.AMOUNT) }
    var amount by remember { mutableStateOf("2,500.00") }
    var selectedMethod by remember { mutableStateOf<WithdrawMethod?>(null) }

    when (currentStep) {
        WithdrawStep.AMOUNT -> WithdrawAmountScreen(
            amount = amount,
            onAmountChange = { amount = it },
            onContinue = { currentStep = WithdrawStep.METHOD },
            onNavigateBack = onNavigateBack
        )
        WithdrawStep.METHOD -> WithdrawMethodScreen(
            selectedMethod = selectedMethod,
            onMethodSelected = { selectedMethod = it },
            onContinue = { currentStep = WithdrawStep.CONFIRMATION },
            onNavigateBack = { currentStep = WithdrawStep.AMOUNT }
        )
        WithdrawStep.CONFIRMATION -> WithdrawConfirmationScreen(
            amount = amount,
            method = selectedMethod,
            onConfirm = { currentStep = WithdrawStep.SUCCESS },
            onCancel = { currentStep = WithdrawStep.METHOD },
            onNavigateBack = { currentStep = WithdrawStep.METHOD }
        )
        WithdrawStep.SUCCESS -> WithdrawSuccessScreen(
            amount = amount,
            method = selectedMethod,
            onDone = onNavigateBack
        )
    }
}

private enum class WithdrawStep {
    AMOUNT, METHOD, CONFIRMATION, SUCCESS
}

private data class WithdrawMethod(
    val name: String,
    val icon: ImageVector,
    val details: String,
    val brandIcon: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawAmountScreen(
    amount: String,
    onAmountChange: (String) -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Amount to Withdraw", fontWeight = FontWeight.Bold) },
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
                        text = "Available balance: $9,567.50",
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
                        if (!amount.contains(".")) onAmountChange(amount + ".")
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawMethodScreen(
    selectedMethod: WithdrawMethod?,
    onMethodSelected: (WithdrawMethod) -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val methods = remember {
        listOf(
            WithdrawMethod("PayPal", Icons.Default.Payment, "paypal.com", "drawable/ic_paypal.svg"),
            WithdrawMethod("Google Pay", Icons.Default.Payment, "google.com", "drawable/ic_google.svg"),
            WithdrawMethod("Apple Pay", Icons.Default.Payment, "apple.com", "drawable/ic_apple.svg"),
            WithdrawMethod("MasterCard", Icons.Default.CreditCard, "**** **** **** 4679", "drawable/ic_mastercard.svg"),
            WithdrawMethod("Visa", Icons.Default.CreditCard, "**** **** **** 5567", "drawable/ic_visa.svg")
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Withdraw to", fontWeight = FontWeight.Bold) },
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
                WithdrawMethodItem(
                    method = method,
                    isSelected = selectedMethod == method,
                    onClick = { onMethodSelected(method) }
                )
            }
        }
    }
}

@Composable
private fun WithdrawMethodItem(
    method: WithdrawMethod,
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
                if (method.details.isNotEmpty()) {
                    Text(method.details, fontSize = 12.sp, color = Color.Gray)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawConfirmationScreen(
    amount: String,
    method: WithdrawMethod?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Withdraw", fontWeight = FontWeight.Bold) },
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
            Text("Amount to Withdraw", color = Color.Gray, fontSize = 14.sp)
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
            
            Text("Withdraw to", color = Color.Gray, fontSize = 14.sp)
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
                        Text(method.details, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                    text = "Withdraw to ${method?.name} Account.",
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
                    Text("Confirm Withdraw", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WithdrawSuccessScreen(
    amount: String,
    method: WithdrawMethod?,
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
            Text(text = "You withdraw to ---- ---- ---- 5567", color = Color.Gray)
            Text(text = "VISA", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(24.dp))
            
            WithdrawReceiptRow("You withdraw", "$$amount")
            WithdrawReceiptRow("To", method?.details ?: "")
            WithdrawReceiptRow("Account", method?.name ?: "")
            WithdrawReceiptRow("Date", "Dec 20, 2023 - 09:38 AM")
            WithdrawReceiptRow("Transaction ID", "201220230938")
            WithdrawReceiptRow("Reference ID", "PZY8B3Z5")
            
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Notes", color = Color.Gray, fontSize = 12.sp)
                Text("Withdraw to VISA Account.", fontSize = 14.sp)
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
private fun WithdrawReceiptRow(label: String, value: String) {
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
