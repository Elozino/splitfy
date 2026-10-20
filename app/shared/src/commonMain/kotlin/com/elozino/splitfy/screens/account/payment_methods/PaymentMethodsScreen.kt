package com.elozino.splitfy.screens.account.payment_methods

import com.elozino.splitfy.screens.account.shared.AccountViewModel
import com.elozino.splitfy.screens.account.upgrade_success.benefitListShort
import com.elozino.splitfy.screens.expenses.add.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.api.dto.AddPaymentMethodRequest
import com.elozino.splitfy.domain.model.PaymentMethodType
import com.elozino.splitfy.ui.theme.primaryLight
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddPayment: () -> Unit,
    onNavigateToSuccess: () -> Unit,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val methods = state.paymentMethods

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.isPaymentProcessing) {
        if (state.isPaymentProcessing) {
            delay(1200)
            viewModel.setPaymentProcessing(false)
            onNavigateToSuccess()
        }
    }

    if (state.isPaymentProcessing) {
        ProcessingPaymentOverlay()
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    if (state.paymentStep == 0) "Payment Methods" else "Review Summary", fontWeight = FontWeight.Bold
                )
            }, navigationIcon = {
                IconButton(onClick = {
                    if (state.paymentStep == 0) onNavigateBack() else viewModel.setPaymentStep(0)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }, actions = {
                IconButton(onClick = onNavigateToAddPayment) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )
    }, containerColor = Color.White, bottomBar = {
        Box(modifier = Modifier.navigationBarsPadding().padding(24.dp)) {
            Button(
                onClick = {
                    if (state.paymentStep == 0) viewModel.setPaymentStep(1)
                    else viewModel.setPaymentProcessing(true)
                },
                enabled = state.selectedPaymentMethodId != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
            ) {
                Text(
                    if (state.paymentStep == 0) "Continue" else "Confirm Payment",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            if (state.paymentStep == 0) {
                Spacer(modifier = Modifier.height(16.dp))

                if (methods.isEmpty()) {
                    Text(
                        text = "No payment methods yet. Add a card to top up or withdraw.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }

                methods.forEach { method ->
                    PaymentMethodItem(
                        label = method.maskedLabel, icon = when (method.type) {
                            PaymentMethodType.CARD -> Icons.Default.CreditCard
                            PaymentMethodType.BANK_ACCOUNT -> Icons.Default.AccountBalanceWallet
                            else -> Icons.Default.Payment
                        }, isSelected = state.selectedPaymentMethodId == method.id, onClick = {
                            viewModel.selectPaymentMethod(method.id)
                            // Selecting is also how the default is changed.
                            viewModel.makeDefault(method.id)
                        }, onDelete = { viewModel.deletePaymentMethod(method.id) })
                }

                state.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(message, color = Color(0xFFD32F2F), fontSize = 14.sp)
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("splitfy Premium", fontWeight = FontWeight.Bold, color = primaryLight)
                        Text(
                            "$49.99 / year",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        benefitListShort().forEach { benefit ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = primaryLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(benefit, fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text("Selected Payment Method", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = methods.firstOrNull { it.id == state.selectedPaymentMethodId }?.maskedLabel
                                ?: "No method selected", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Change",
                            color = primaryLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { viewModel.setPaymentStep(0) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).border(
            1.2.dp, if (isSelected) primaryLight else Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(20.dp)
        ).clip(RoundedCornerShape(20.dp)).clickable { onClick() }.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (label.contains("Pay")) Color.Unspecified else Color.Black,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 16.sp)
        onDelete?.let { delete ->
            IconButton(onClick = delete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove payment method",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        RadioButton(
            selected = isSelected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = primaryLight)
        )
    }
}

@Composable
private fun ProcessingPaymentOverlay() {
    Surface(
        modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(180.dp), shape = RoundedCornerShape(32.dp), color = Color.White
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = primaryLight, strokeWidth = 4.dp, modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Processing Payment...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
