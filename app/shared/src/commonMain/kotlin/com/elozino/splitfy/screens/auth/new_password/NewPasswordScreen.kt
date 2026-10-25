package com.elozino.splitfy.screens.auth.new_password

import androidx.compose.foundation.layout.consumeWindowInsets
import com.elozino.splitfy.screens.auth.shared.AuthFormLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.ui.components.AuthFieldError
import com.elozino.splitfy.ui.components.AuthTextField
import com.elozino.splitfy.ui.components.PrimaryButton
import com.elozino.splitfy.ui.components.SuccessDialog
import kotlinx.coroutines.delay
import com.elozino.splitfy.screens.auth.shared.AuthViewModel
import com.elozino.splitfy.screens.expenses.add.label

@Composable
fun NewPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit = {},
    onPasswordChanged: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val newPassword = state.newPassword

    if (state.passwordReset) {
        SuccessDialog(
            onDismissRequest = {},
            icon = Icons.Default.Lock,
            title = "Reset Password Successful!",
            message = "Please wait...\nYou will be directed to the homepage."
        )
        LaunchedEffect(Unit) {
            delay(1200)
            onPasswordChanged()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { padding ->
        AuthFormLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(horizontal = 24.dp),
            header = { titleSize ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Create new password",
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔒",
                        fontSize = titleSize
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "You're almost there! Please create a new password for your Splitfy account.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            },
        ) {
            AuthTextField(
                value = newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = "New Password",
                placeholder = "New Password",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (state.isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleNewPasswordVisibility) {
                        Icon(
                            imageVector = if (state.isNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            AuthTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "Confirm New Password",
                placeholder = "Confirm New Password",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (state.isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleConfirmPasswordVisibility) {
                        Icon(
                            imageVector = if (state.isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.weight(1f))

            AuthFieldError(state.fieldErrors["newPassword"])
            AuthFieldError(
                if (state.confirmPassword.isNotEmpty() && state.confirmPassword != newPassword) {
                    "Passwords do not match"
                } else {
                    null
                }
            )
            AuthFieldError(state.errorMessage)

            PrimaryButton(
                text = if (state.isSubmitting) "Saving…" else "Continue",
                onClick = viewModel::resetPassword,
                enabled = newPassword.isNotEmpty() &&
                    state.confirmPassword == newPassword &&
                    !state.isSubmitting
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

