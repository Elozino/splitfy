package com.elozino.splitfy.screens.account.personal_info

import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.components.UserAvatar
import com.preat.peekaboo.image.picker.ResizeOptions
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonalInfoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // The picker only downsizes; the view model crops to a square.
    val avatarPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        resizeOptions = ResizeOptions(width = 1024, height = 1024, compressionQuality = 0.9),
        onResult = { images -> images.firstOrNull()?.let(viewModel::onAvatarPicked) },
    )

    // Leave the screen once the change has landed on the server.
    LaunchedEffect(state.savedAt) {
        if (state.savedAt != null) onNavigateBack()
    }
    LaunchedEffect(state.emailChangedTo) {
        state.emailChangedTo?.let {
            snackbar.showSnackbar("Your email is now $it")
            viewModel.consumeEmailChanged()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Personal Info", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(modifier = Modifier.navigationBarsPadding().padding(24.dp)) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving && state.user != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
                ) {
                    Text(
                        text = if (state.isSaving) "Saving…" else "Save",
                        color = AppTheme.colors.onBrand,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(100.dp).clickable(
                            enabled = state.uploadsEnabled && !state.isUploadingAvatar,
                            onClickLabel = "Change photo",
                        ) { avatarPicker.launch() }
                    ) {
                        UserAvatar(state.avatarUrl, state.fullName.ifBlank { state.email }, size = 100.dp)
                        if (state.isUploadingAvatar) {
                            CircularProgressIndicator(color = AppTheme.colors.brand, modifier = Modifier.align(Alignment.Center))
                        }
                        if (state.uploadsEnabled) {
                            Box(
                                modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                                    .background(AppTheme.colors.brand, CircleShape).border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    if (state.uploadsEnabled && state.avatarUrl != null) {
                        TextButton(onClick = viewModel::removeAvatar, enabled = !state.isUploadingAvatar) {
                            Text("Remove photo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                InfoField("Full Name", state.fullName, onValueChange = viewModel::onFullNameChange)
                state.fieldErrors["displayName"]?.let { FieldError(it) }

                // The address identifies the account, so it changes through a verified
                // flow rather than a free-text edit.
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Email", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .background(AppTheme.colors.field, RoundedCornerShape(12.dp))
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.email, modifier = Modifier.weight(1f), color = Color.DarkGray)
                        TextButton(onClick = viewModel::startEmailChange, enabled = state.user != null) {
                            Text("Change", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                InfoField("Phone Number", state.phone, onValueChange = viewModel::onPhoneChange)
                state.fieldErrors["phoneNumber"]?.let { FieldError(it) }

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Default currency", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("New groups start in this currency.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PersonalInfoState.currencyChoices.forEach { currency ->
                            FilterChip(
                                selected = currency == state.currency,
                                onClick = { viewModel.onCurrencyChange(currency) },
                                label = { Text(currency.code) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppTheme.colors.brand, selectedLabelColor = AppTheme.colors.onBrand),
                            )
                        }
                    }
                }
                state.errorMessage?.let { FieldError(it) }
            }
        }
    }

    if (state.emailChangeStep != EmailChangeStep.CLOSED) {
        EmailChangeDialog(state, viewModel)
    }
}

@Composable
private fun EmailChangeDialog(state: PersonalInfoState, viewModel: PersonalInfoViewModel) {
    val enteringCode = state.emailChangeStep == EmailChangeStep.ENTER_CODE
    AlertDialog(
        onDismissRequest = viewModel::dismissEmailChange,
        title = { Text(if (enteringCode) "Check your new inbox" else "Change email", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (enteringCode) {
                    Text("Enter the 6-digit code we sent to ${state.pendingEmail}.", fontSize = 14.sp)
                    DialogField(state.code, "Code", KeyboardType.NumberPassword, viewModel::onCodeChange)
                    TextButton(onClick = viewModel::submitNewEmail, enabled = !state.isEmailChangeBusy) {
                        Text("Send a new code", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    Text(
                        "We'll send a code to the new address. Your current email keeps working until you enter it.",
                        fontSize = 14.sp,
                    )
                    DialogField(state.newEmail, "New email", KeyboardType.Email, viewModel::onNewEmailChange)
                }
                state.emailChangeError?.let { FieldError(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (enteringCode) viewModel::submitCode else viewModel::submitNewEmail,
                enabled = !state.isEmailChangeBusy,
            ) {
                if (state.isEmailChangeBusy) {
                    CircularProgressIndicator(color = AppTheme.colors.brand, modifier = Modifier.size(20.dp))
                } else {
                    Text(if (enteringCode) "Confirm" else "Send code", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissEmailChange) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun DialogField(value: String, placeholder: String, keyboardType: KeyboardType, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = AppTheme.colors.field,
            focusedContainerColor = AppTheme.colors.field,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
internal fun FieldError(message: String) {
    Text(
        text = message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
internal fun InfoField(
    label: String,
    value: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = AppTheme.colors.field,
                focusedContainerColor = AppTheme.colors.field,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}
