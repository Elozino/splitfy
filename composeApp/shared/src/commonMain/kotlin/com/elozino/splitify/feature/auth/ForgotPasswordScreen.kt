package com.elozino.splitify.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.feature.auth.component.AuthTextField
import com.elozino.splitify.feature.auth.component.PrimaryButton

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit = {},
    onOtpSent: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }

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
                    text = "Reset your password",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
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
                color = Color.Gray,
                lineHeight = 24.sp
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

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Continue",
                onClick = onOtpSent,
                enabled = email.isNotEmpty()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
