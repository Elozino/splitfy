package com.elozino.splitfy.screens.auth.login

import androidx.compose.foundation.clickable
import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.ui.components.AuthTextField
import com.elozino.splitfy.ui.components.PrimaryButton
import com.elozino.splitfy.ui.components.SuccessDialog
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import com.elozino.splitfy.screens.auth.shared.AuthViewModel
import com.elozino.splitfy.screens.expenses.add.label

@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    onTwoFactorRequired: () -> Unit = {},
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.twoFactorChallenge) {
        if (state.twoFactorChallenge != null) onTwoFactorRequired()
    }
    val email = state.email
    val password = state.password

    // The dialog is driven by the session, not by the tap: it only appears once the
    // backend has actually issued a token.
    if (state.isAuthenticated) {
        SuccessDialog(onDismissRequest = {})
        LaunchedEffect(Unit) {
            delay(1200)
            onLoginSuccess()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Welcome back",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "👋",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Please enter your email & password to sign in.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            AuthTextField(
                value = email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                placeholder = "Email",
                leadingIcon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            state.fieldErrors["email"]?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            AuthTextField(
                value = password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                placeholder = "Password",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (state.isLoginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleLoginPasswordVisibility) {
                        Icon(
                            imageVector = if (state.isLoginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            state.fieldErrors["password"]?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.rememberLogin,
                        onCheckedChange = viewModel::onRememberLoginChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = AppTheme.colors.brand,
                            uncheckedColor = AppTheme.colors.brand
                        )
                    )
                    Text(
                        text = "Remember me",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Text(
                    text = "Forgot password?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.brand,
                    modifier = Modifier.clickable { onNavigateToForgotPassword() }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = if (state.isSubmitting) "Logging in…" else "Log in",
                onClick = viewModel::login,
                enabled = state.canSubmitLogin
            )

            Spacer(modifier = Modifier.height(24.dp))

            val signUpText = buildAnnotatedString {
                append("Don't have an account? ")
                withStyle(style = SpanStyle(color = AppTheme.colors.brand, fontWeight = FontWeight.Bold)) {
                    append("Sign up")
                }
            }
            
            Text(
                text = signUpText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSignup() }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
