package com.elozino.splitify.screens

import androidx.compose.foundation.layout.*
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
import com.elozino.splitify.ui.components.AuthFieldError
import com.elozino.splitify.ui.components.AuthTextField
import com.elozino.splitify.ui.components.PrimaryButton
import com.elozino.splitify.ui.components.SuccessDialog
import kotlinx.coroutines.delay

@Composable
fun NewPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit = {},
    onPasswordChanged: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

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
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Create new password",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🔒",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "You're almost there! Please create a new password for your Splitify account.",
                fontSize = 16.sp,
                color = Color.Gray,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            AuthTextField(
                value = newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = "New Password",
                placeholder = "New Password",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.Black
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            AuthTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm New Password",
                placeholder = "Confirm New Password",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.Black
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.weight(1f))

            AuthFieldError(state.fieldErrors["newPassword"])
            AuthFieldError(
                if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) {
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
                    confirmPassword == newPassword &&
                    !state.isSubmitting
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
