package com.elozino.splitify.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun OtpScreen(
    onNavigateBack: () -> Unit = {},
    onOtpVerified: () -> Unit = {}
) {
    var otpValues by remember { mutableStateOf(listOf("", "", "", "")) }
    var timeLeft by remember { mutableStateOf(55) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
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
                    text = "OTP code verification",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🔐",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val emailText = buildAnnotatedString {
                append("We have sent an OTP code to your email and")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) {
                    append("*******ley@gmail.com")
                }
                append(". Enter the OTP code below to verify.")
            }

            Text(
                text = emailText,
                fontSize = 16.sp,
                color = Color.Gray,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                otpValues.forEachIndexed { index, value ->
                    OtpBox(
                        value = value,
                        isFocused = false, // Simple implementation without active focus tracking
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Didn't receive email?",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You can resend code in $timeLeft s",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.weight(1f))

            // Custom Number Keyboard (Simplified)
            NumberKeyboard(
                onNumberClick = { num ->
                    val emptyIndex = otpValues.indexOfFirst { it.isEmpty() }
                    if (emptyIndex != -1) {
                        val newList = otpValues.toMutableList()
                        newList[emptyIndex] = num
                        otpValues = newList
                        if (newList.all { it.isNotEmpty() }) {
                            onOtpVerified()
                        }
                    }
                },
                onDeleteClick = {
                    val lastIndex = otpValues.indexOfLast { it.isNotEmpty() }
                    if (lastIndex != -1) {
                        val newList = otpValues.toMutableList()
                        newList[lastIndex] = ""
                        otpValues = newList
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun OtpBox(
    value: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(60.dp)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (value.isNotEmpty()) Color(0xFFFEBB1B) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
fun NumberKeyboard(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numbers = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "⌫")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        numbers.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { char ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clickable {
                                if (char == "⌫") onDeleteClick()
                                else if (char != "*") onNumberClick(char)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
