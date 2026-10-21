package com.elozino.splitfy.screens.account.security_settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.screens.account.security_settings.ChangePasswordViewModel.Companion.FIELD_CONFIRM
import com.elozino.splitfy.screens.account.security_settings.ChangePasswordViewModel.Companion.FIELD_CURRENT
import com.elozino.splitfy.screens.account.security_settings.ChangePasswordViewModel.Companion.FIELD_NEW
import com.elozino.splitfy.ui.components.AuthFieldError
import com.elozino.splitfy.ui.components.AuthTextField
import com.elozino.splitfy.ui.components.PrimaryButton
import com.elozino.splitfy.ui.components.SuccessDialog
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Change the password, or (for a Google/Apple account) set the first one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    passwordSet: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: ChangePasswordViewModel = koinViewModel { parametersOf(passwordSet) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isDone) {
        SuccessDialog(onDismissRequest = onNavigateBack)
        androidx.compose.runtime.LaunchedEffect(Unit) {
            delay(1500)
            onNavigateBack()
        }
    }

    val transformation = if (state.passwordsVisible) VisualTransformation.None else PasswordVisualTransformation()
    val visibilityToggle: @Composable () -> Unit = {
        IconButton(onClick = viewModel::togglePasswordsVisible) {
            Icon(
                if (state.passwordsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (state.passwordsVisible) "Hide passwords" else "Show passwords",
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (state.passwordSet) "Change password" else "Set a password", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (state.passwordSet) {
                    "After you change it, every other device signed in to your account is signed out. This one stays signed in."
                } else {
                    "Your account was created with Google or Apple, so it has no password yet. Set one to also sign in with your email."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))

            if (state.passwordSet) {
                AuthTextField(
                    value = state.currentPassword,
                    onValueChange = viewModel::onCurrentPasswordChange,
                    label = "Current password",
                    placeholder = "Current password",
                    leadingIcon = Icons.Default.Lock,
                    visualTransformation = transformation,
                    trailingIcon = visibilityToggle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                AuthFieldError(state.fieldErrors[FIELD_CURRENT])
                Spacer(Modifier.height(16.dp))
            }

            AuthTextField(
                value = state.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = "New password",
                placeholder = "At least 8 characters, with a letter and a number",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = transformation,
                trailingIcon = visibilityToggle.takeUnless { state.passwordSet },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            AuthFieldError(state.fieldErrors[FIELD_NEW])
            Spacer(Modifier.height(16.dp))

            AuthTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "Confirm new password",
                placeholder = "Type it again",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            AuthFieldError(state.fieldErrors[FIELD_CONFIRM])
            AuthFieldError(state.errorMessage)

            Spacer(Modifier.height(32.dp))
            PrimaryButton(
                text = when {
                    state.isSubmitting -> "Saving…"
                    state.passwordSet -> "Change password"
                    else -> "Set password"
                },
                onClick = viewModel::submit,
                enabled = state.canSubmit,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
