package com.elozino.splitify.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.feature.auth.component.AuthTextField
import com.elozino.splitify.feature.auth.component.PrimaryButton
import com.elozino.splitify.feature.auth.component.SuccessDialog
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    if (showSuccessDialog) {
        SuccessDialog(
            onDismissRequest = { showSuccessDialog = false }
        )
        LaunchedEffect(Unit) {
            delay(2000)
            onLoginSuccess()
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
                    text = "Welcome back",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
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
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            AuthTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "Email",
                leadingIcon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(24.dp))

            AuthTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "Password",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.Black
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFFEBB1B),
                            uncheckedColor = Color(0xFFFEBB1B)
                        )
                    )
                    Text(
                        text = "Remember me",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
                
                Text(
                    text = "Forgot password?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFEBB1B),
                    modifier = Modifier.clickable { onNavigateToForgotPassword() }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Sign in",
                onClick = { showSuccessDialog = true },
                enabled = email.isNotEmpty() && password.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(24.dp))

            val signUpText = buildAnnotatedString {
                append("Don't have an account? ")
                withStyle(style = SpanStyle(color = Color(0xFFFEBB1B), fontWeight = FontWeight.Bold)) {
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
