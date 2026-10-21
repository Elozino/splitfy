package com.elozino.splitfy.screens.auth.forgot_password

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.ui.components.AuthFieldError
import com.elozino.splitfy.ui.components.AuthTextField
import com.elozino.splitfy.ui.components.PrimaryButton
import com.elozino.splitfy.screens.auth.shared.AuthViewModel
import com.elozino.splitfy.screens.expenses.add.label

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit = {},
    onOtpSent: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val email = state.email

    // Advance only once the backend has accepted the request.
    LaunchedEffect(state.resetCodeSent) {
        if (state.resetCodeSent) onOtpSent()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Reset your password",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🔑",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Please enter your email and we will send an OTP code in the next step to reset your password.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            AuthTextField(
                value = email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                placeholder = "Email",
                leadingIcon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.weight(1f))

            AuthFieldError(state.fieldErrors["email"])
            AuthFieldError(state.errorMessage)

            PrimaryButton(
                text = if (state.isSubmitting) "Sending…" else "Continue",
                onClick = viewModel::requestPasswordReset,
                enabled = email.isNotEmpty() && !state.isSubmitting
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

