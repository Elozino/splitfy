package com.elozino.splitfy.screens.account.upgrade_plan

import com.elozino.splitfy.screens.common.rememberActionBarMetrics
import androidx.compose.material3.MaterialTheme
import com.elozino.splitfy.ui.theme.AppTheme
import com.elozino.splitfy.screens.account.shared.AccountViewModel

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
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradePlanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPayment: () -> Unit,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Upgrade Plan", fontWeight = FontWeight.Bold) }, navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }, containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        val bar = rememberActionBarMetrics()
        Box(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = bar.verticalPadding)) {
            Button(
                onClick = onNavigateToPayment,
                modifier = Modifier.fillMaxWidth().height(bar.buttonHeight),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
            ) {
                Text(
                    "Continue - ${if (state.selectedBillingTab == 0) "$4.99" else "$49.99"}",
                    color = AppTheme.colors.onBrand,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colors.field
            ) {
                Row {
                    listOf("Monthly", "Yearly").forEachIndexed { index, title ->
                        val isSelected = state.selectedBillingTab == index
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppTheme.colors.brand else Color.Transparent)
                                .clickable { viewModel.selectBillingTab(index) }, contentAlignment = Alignment.Center
                        ) {
                            Text(
                                title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Splitfy Premium", fontWeight = FontWeight.Bold, color = AppTheme.colors.brand, fontSize = 18.sp
                )

                Row(
                    verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text(
                        if (state.selectedBillingTab == 0) "$4.99" else "$49.99", fontSize = 48.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (state.selectedBillingTab == 0) " / month" else " / year",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (state.selectedBillingTab == 1) {
                    Surface(
                        color = AppTheme.colors.brand.copy(alpha = 0.2f), shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            "Save 15%",
                            color = AppTheme.colors.brand,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                val benefits = listOf(
                    "All basic benefits plus",
                    "Ad-free experience",
                    "Priority customer support",
                    "Access to detailed spending insights",
                    "Unlimited bill-sharing groups",
                    "Early access to new features"
                )

                benefits.forEach { benefit ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = AppTheme.colors.brand,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(benefit, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
