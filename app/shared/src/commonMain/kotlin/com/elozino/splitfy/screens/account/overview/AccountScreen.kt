package com.elozino.splitfy.screens.account.overview

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
import com.elozino.splitfy.ui.theme.primaryLight
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onNavigateToUpgrade: () -> Unit,
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateToNotification: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToPaymentMethods: () -> Unit,
    onNavigateToBilling: () -> Unit,
    onNavigateToLinkedAccounts: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToDataAnalytics: () -> Unit,
    onNavigateToHelpSupport: () -> Unit,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-read on entry so a profile edit elsewhere is reflected here.
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Account", fontWeight = FontWeight.Bold) }, navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = "Logo",
                        modifier = Modifier.padding(start = 16.dp).size(28.dp),
                        tint = Color.Black
                    )
                }, actions = {
                    IconButton(onClick = { viewModel.setQrDialogVisible(true) }) {
                        Icon(Icons.Default.QrCode, contentDescription = "My QR Code")
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }, containerColor = Color.White
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(
                top = padding.calculateTopPadding(),
                start = padding.calculateStartPadding(
                    layoutDirection = LayoutDirection.Ltr
                ),
                end = padding.calculateEndPadding(
                    layoutDirection = LayoutDirection.Ltr
                )
            ), contentPadding = PaddingValues(top = 24.dp, start = 24.dp, end = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.LightGray)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.displayName.ifEmpty { "Your profile" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(state.email, color = Color.Gray, fontSize = 14.sp)
                        if (!state.balance.isZero) {
                            Text(
                                text = "Balance ${state.balance.toPlainString()} ${state.balance.currency.code}",
                                color = Color(0xFF2E7D32),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToPersonalInfo) {
                        Icon(
                            Icons.Default.Edit, contentDescription = "Edit Profile", tint = primaryLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToUpgrade() },
                    shape = RoundedCornerShape(24.dp),
                    color = primaryLight
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Upgrade Plan to Unlock More!", fontWeight = FontWeight.Bold, fontSize = 15.sp
                            )
                            Text(
                                "Get full access with splitfy premium plans",
                                fontSize = 11.sp,
                                color = Color.Black.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            val menuItems = listOf(
                AccountMenuItem("Personal Info", Icons.Default.Person, onNavigateToPersonalInfo), AccountMenuItem(
                    "Notification", Icons.Default.Notifications, onNavigateToNotification
                ), AccountMenuItem("Account & Security", Icons.Default.Security, onNavigateToSecurity), AccountMenuItem(
                    "Payment Methods", Icons.Default.Payment, onNavigateToPaymentMethods
                ), AccountMenuItem(
                    "Billing & Subscriptions", Icons.AutoMirrored.Filled.ReceiptLong, onNavigateToBilling
                ), AccountMenuItem("Linked Accounts", Icons.Default.Link, onNavigateToLinkedAccounts), AccountMenuItem(
                    "App Appearance", Icons.Default.RemoveRedEye, onNavigateToAppearance
                ), AccountMenuItem(
                    "Data & Analytics", Icons.Default.BarChart, onNavigateToDataAnalytics
                ), AccountMenuItem(
                    "Help & Support", Icons.AutoMirrored.Filled.HelpOutline, onNavigateToHelpSupport
                )
            )

            items(menuItems) { item ->
                AccountRow(item)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = !state.isSigningOut) { viewModel.setLogoutDialogVisible(true) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isSigningOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.Red
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        if (state.isSigningOut) "Logging out…" else "Logout",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (state.isQrDialogVisible) {
        MyQrCodeDialog(onDismiss = { viewModel.setQrDialogVisible(false) })
    }

    if (state.isLogoutDialogVisible) {
        LogoutDialog(isLoggingOut = state.isSigningOut, onDismiss = { viewModel.setLogoutDialogVisible(false) }, onLogout = {
            // Clears the session; the navigation graph unwinds to sign-in.
            viewModel.signOut()
        })
    }

    // The dialog dismisses itself once sign-out finishes.
    LaunchedEffect(state.signedOut) {
        if (state.signedOut) viewModel.setLogoutDialogVisible(false)
    }
}

@Composable
private fun AccountRow(item: AccountMenuItem) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { item.onClick() }.padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(Color(0xFFF5F5F5), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            item.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray
        )
    }
}

private data class AccountMenuItem(
    val title: String, val icon: ImageVector, val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQrCodeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }, title = {
            Text(
                "My QR Code",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }, text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(200.dp).background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp)).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "QR Code Personal: scan user for quick wallet payments.",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }, containerColor = Color.White, shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun LogoutDialog(isLoggingOut: Boolean = false, onDismiss: () -> Unit, onLogout: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isLoggingOut) onDismiss() }, title = {
            Text(
                "Logout",
                fontWeight = FontWeight.Bold,
                color = Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }, text = {
            Text(
                "Are you sure you want to logout?", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }, confirmButton = {
            Button(
                onClick = onLogout,
                enabled = !isLoggingOut,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
            ) {
                if (isLoggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black
                    )
                } else {
                    Text("Yes, Logout", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }, dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoggingOut,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, primaryLight)
            ) {
                Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }, containerColor = Color.White, shape = RoundedCornerShape(24.dp)
    )
}
