package com.elozino.splitfy.screens.auth.signup

import androidx.compose.foundation.clickable
import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
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
import com.elozino.splitfy.ui.components.AuthFieldError
import com.elozino.splitfy.ui.components.AuthTextField
import com.elozino.splitfy.ui.components.PrimaryButton
import com.elozino.splitfy.ui.components.SuccessDialog
import kotlinx.coroutines.delay
import com.elozino.splitfy.screens.auth.shared.AuthViewModel
import com.elozino.splitfy.screens.expenses.add.label

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onSignUpSuccess: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fullName = state.displayName
    val email = state.email
    val password = state.password

    // Success is driven by the account actually existing, not by the tap.
    if (state.isAuthenticated) {
        SuccessDialog(
            onDismissRequest = {},
            title = "Account Created!",
            message = "Your account has been created successfully. Please wait while we redirect you."
        )
        LaunchedEffect(Unit) {
            delay(1200)
            onSignUpSuccess()
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
                    text = "Create an account",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "✨",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Join splitfy today and start splitting bills with ease.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            AuthTextField(
                value = fullName,
                onValueChange = viewModel::onDisplayNameChange,
                label = "Full Name",
                placeholder = "Full Name",
                leadingIcon = Icons.Default.Person,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(modifier = Modifier.height(24.dp))

            AuthTextField(
                value = email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                placeholder = "Email",
                leadingIcon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(24.dp))

            AuthTextField(
                value = password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                placeholder = "Password",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (state.isSignUpPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleSignUpPasswordVisibility) {
                        Icon(
                            imageVector = if (state.isSignUpPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            AuthFieldError(state.fieldErrors["displayName"])
            AuthFieldError(state.fieldErrors["email"])
            AuthFieldError(state.fieldErrors["password"])
            AuthFieldError(state.errorMessage)

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = if (state.isSubmitting) "Creating account…" else "Sign up",
                onClick = viewModel::signUp,
                enabled = state.canSubmitSignUp
            )

            Spacer(modifier = Modifier.height(24.dp))

            val loginText = buildAnnotatedString {
                append("Already have an account? ")
                withStyle(style = SpanStyle(color = AppTheme.colors.brand, fontWeight = FontWeight.Bold)) {
                    append("Log in")
                }
            }

            Text(
                text = loginText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLogin() }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
