package com.elozino.splitfy.screens.account.security_settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.screens.account.shared.AccountViewModel
import com.elozino.splitfy.security.DeviceAuthAvailability
import com.elozino.splitfy.security.DeviceAuthResult
import com.elozino.splitfy.security.label
import com.elozino.splitfy.security.rememberDeviceAuthenticator
import com.elozino.splitfy.ui.components.LoadErrorState
import com.elozino.splitfy.ui.components.RefreshableBox
import com.elozino.splitfy.ui.components.SkeletonList
import com.elozino.splitfy.ui.components.SkeletonListItem
import com.elozino.splitfy.ui.theme.primaryLight
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChangePassword: (hasPassword: Boolean) -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToTwoFactorSetup: () -> Unit,
    viewModel: SecurityViewModel = koinViewModel(),
    accountViewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by accountViewModel.state.collectAsStateWithLifecycle()
    val authenticator = rememberDeviceAuthenticator()
    val biometrics = remember(authenticator) { authenticator.availability() as? DeviceAuthAvailability.Biometric }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (account.isDeleteAccountDialogVisible) {
        DeleteAccountDialog(
            isDeleting = account.isDeletingAccount,
            error = account.deleteAccountError,
            onConfirm = accountViewModel::deleteAccount,
            onDismiss = { accountViewModel.setDeleteAccountDialogVisible(false) },
        )
    }

    if (state.isDisableDialogVisible) {
        DisableTwoFactorDialog(
            state = state,
            onInputChange = viewModel::onDisableInputChange,
            onToggleMethod = viewModel::toggleDisableMethod,
            onConfirm = viewModel::disableTwoFactor,
            onDismiss = { viewModel.showDisableDialog(false) },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        val overview = state.overview
        when {
            overview == null && state.isLoading -> SkeletonList(
                modifier = Modifier.padding(padding),
                count = 4,
                item = { SkeletonListItem(avatarSize = 24.dp, trailing = false) },
            )
            overview == null -> LoadErrorState(
                message = state.loadError ?: "Couldn't load your security settings",
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            else -> RefreshableBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                indicatorPadding = padding,
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                    item { SectionTitle("Signing in") }
                    item {
                        SettingRow(
                            icon = Icons.Default.Password,
                            title = if (overview.hasPassword) "Change password" else "Set a password",
                            subtitle = if (overview.hasPassword) {
                                "Other devices are signed out when you change it"
                            } else {
                                "You sign in with Google or Apple. Add a password to sign in with your email too."
                            },
                            onClick = { onNavigateToChangePassword(overview.hasPassword) },
                        )
                    }
                    item {
                        SettingRow(
                            icon = Icons.Default.VerifiedUser,
                            title = "Two-step verification",
                            subtitle = if (overview.twoFactorEnabled) {
                                "On · ${overview.recoveryCodesRemaining} recovery codes left"
                            } else {
                                "Off · ask for a code from an authenticator app when signing in"
                            },
                            onClick = {
                                if (overview.twoFactorEnabled) viewModel.showDisableDialog(true) else onNavigateToTwoFactorSetup()
                            },
                            trailing = {
                                Text(
                                    if (overview.twoFactorEnabled) "Turn off" else "Set up",
                                    color = if (overview.twoFactorEnabled) MaterialTheme.colorScheme.error else primaryLight,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                        )
                    }
                    item {
                        SettingRow(
                            icon = Icons.Default.Devices,
                            title = "Devices",
                            subtitle = "See where you're signed in and sign devices out",
                            onClick = onNavigateToDevices,
                        )
                    }

                    // Without enrolled biometrics there is nothing to offer here, so the
                    // section is left out rather than shown disabled.
                    if (biometrics != null) {
                        item { SectionTitle("This device") }
                        item {
                            SettingRow(
                                icon = Icons.Default.Fingerprint,
                                title = "App lock",
                                subtitle = "Require ${biometrics.kind.label} to open splitfy",
                                onClick = null,
                                trailing = {
                                    Switch(
                                        checked = state.appLockEnabled,
                                        onCheckedChange = { enable ->
                                            if (!enable) {
                                                viewModel.setAppLockEnabled(false)
                                            } else {
                                                // Prove it works before relying on it.
                                                scope.launch {
                                                    val result = authenticator.authenticate("Confirm it's you to turn on App lock")
                                                    if (result == DeviceAuthResult.Success) viewModel.setAppLockEnabled(true)
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryLight),
                                    )
                                },
                            )
                        }
                        if (state.appLockEnabled) {
                            item {
                                LockAfterRow(
                                    minutes = state.appLockTimeoutMinutes,
                                    onSelect = viewModel::setAppLockTimeout,
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider(Modifier.padding(top = 24.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Delete Account",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { accountViewModel.setDeleteAccountDialogVisible(true) }
                                .padding(vertical = 8.dp),
                        )
                        Text(
                            "Permanently removes your profile and sign-in details. You need to be settled up in every group first.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = primaryLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun LockAfterRow(minutes: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Timer, contentDescription = null, tint = primaryLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Lock after", fontWeight = FontWeight.Medium)
            Text("Time in the background before App lock asks again", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            Text(lockAfterLabel(minutes), color = primaryLight, fontWeight = FontWeight.SemiBold)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                com.elozino.splitfy.security.AppLockSettings.TIMEOUT_CHOICES.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(lockAfterLabel(choice)) },
                        onClick = {
                            onSelect(choice)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun lockAfterLabel(minutes: Int): String = when (minutes) {
    0 -> "Immediately"
    1 -> "1 minute"
    else -> "$minutes minutes"
}

/** A code (authenticator or recovery) or the password, so a borrowed unlocked phone is not enough. */
@Composable
private fun DisableTwoFactorDialog(
    state: SecurityState,
    onInputChange: (String) -> Unit,
    onToggleMethod: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.isDisabling) onDismiss() },
        title = { Text("Turn off two-step verification?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Signing in will only need your password (or Google/Apple) again. Your recovery codes stop working.",
                )
                OutlinedTextField(
                    value = state.disableInput,
                    onValueChange = onInputChange,
                    label = { Text(if (state.disableWithPassword) "Password" else "Authenticator or recovery code") },
                    singleLine = true,
                    visualTransformation = if (state.disableWithPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (state.disableWithPassword) KeyboardType.Password else KeyboardType.Ascii,
                    ),
                    isError = state.disableError != null,
                    supportingText = state.disableError?.let { { Text(it) } },
                )
                TextButton(onClick = onToggleMethod) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.disableWithPassword) "Use a code instead" else "Use your password instead")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = state.canSubmitDisable,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                if (state.isDisabling) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Turn off")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !state.isDisabling) { Text("Cancel") }
        },
    )
}

/**
 * Deletion cannot be undone, so it takes an explicit second step. A refusal from the
 * server (an unsettled group, usually) is shown inside the dialog, naming the groups.
 */
@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("Delete your account?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your name, email, phone number, photo, password and linked Google or Apple " +
                        "sign-ins will be deleted and you will be signed out on every device. " +
                        "This cannot be undone.",
                )
                Text(
                    "Expenses you were part of stay in their groups so everyone else's balances " +
                        "still add up; they will show you as \"Deleted user\".",
                    color = Color.Gray,
                    fontSize = 13.sp,
                )
                error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Delete account")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") }
        },
    )
}
