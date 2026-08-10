package com.elozino.splitify.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.elozino.splitify.resources.Res
import com.elozino.splitify.resources.ic_apple

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AuthScreen(
    onNavigateToRegister: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToSocialAuth: (AuthType) -> Unit = {}
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Register Button
                val registerOption = authOptions.find { it.title == "Sign up" }
                registerOption?.let { option ->
                    AuthButton(
                        option = option,
                        onClick = onNavigateToRegister
                    )
                }

                // Login Button
                val loginOption = authOptions.find { it.title == "Log in" }
                loginOption?.let { option ->
                    AuthButton(
                        option = option,
                        onClick = onNavigateToLogin
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "Let's get started!",
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                lineHeight = 40.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "With Splitify, expenses and bill splitting are easier than ever before",
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Social Auth Options
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(authOptions.filter { it.type != AuthType.Email }) { option ->
                    AuthButton(
                        option = option,
                        onClick = { onNavigateToSocialAuth(option.type) }
                    )
                }
            }
        }
    }
}

@Composable
fun AuthButton(
    option: AuthOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTransparent = option.backgroundColor == Color.Transparent
    val isWhite = option.backgroundColor == Color.White
    val primaryColor = Color(0xFFFEBB1B)

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = option.backgroundColor,
            contentColor = if (isTransparent) {
                primaryColor
            } else if (isWhite) {
                Color.Black
            } else {
                Color.White
            }
        ),
        border = if (isTransparent) {
            BorderStroke(1.dp, primaryColor)
        } else if (isWhite) {
            BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
        } else {
            null
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isWhite) 1.dp else 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        if (option.type != AuthType.Email) {
            AsyncImage(
                model = Res.getUri(option.iconUri),
                contentDescription = option.title,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = option.title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        // Equal spacer to keep text centered
        Spacer(modifier = Modifier.size(24.dp))
    }
}