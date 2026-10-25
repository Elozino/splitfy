package com.elozino.splitfy.screens.auth.otp

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import com.elozino.splitfy.screens.common.FillOrScrollColumn
import com.elozino.splitfy.screens.common.rememberSideBySide
import androidx.compose.foundation.background
import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.ui.components.AuthFieldError
import kotlinx.coroutines.delay
import com.elozino.splitfy.screens.auth.shared.AuthViewModel

@Composable
fun OtpScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit = {},
    onOtpVerified: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Only move on once the server has exchanged the code for a reset token.
    LaunchedEffect(state.resetToken) {
        if (state.resetToken != null) onOtpVerified()
    }

    CodeEntryScreen(
        title = "OTP code verification",
        emoji = "🔐",
        email = state.email,
        code = state.otpCode,
        errorMessage = state.errorMessage,
        secondsRemaining = state.otpSecondsRemaining,
        onTick = viewModel::tickOtpTimer,
        onCodeChange = viewModel::onOtpChange,
        onComplete = viewModel::verifyOtp,
        onResend = {
            viewModel.resetOtpTimer()
            viewModel.requestPasswordReset()
        },
        onNavigateBack = onNavigateBack,
    )
}

/**
 * The step after sign-up: confirm the email address with the code we sent. It can
 * be skipped; until it is done, nobody can add this account to a group by email.
 */
@Composable
fun VerifyEmailScreen(
    viewModel: AuthViewModel,
    email: String,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.emailVerified) {
        if (state.emailVerified) onDone()
    }

    CodeEntryScreen(
        title = "Confirm your email",
        emoji = "📬",
        email = email,
        code = state.otpCode,
        errorMessage = state.errorMessage,
        secondsRemaining = state.otpSecondsRemaining,
        onTick = viewModel::tickOtpTimer,
        onCodeChange = viewModel::onOtpChange,
        onComplete = viewModel::verifyEmail,
        onResend = {
            viewModel.resetOtpTimer()
            viewModel.resendVerification()
        },
        // Already signed in at this point, so "back" means "later", never the sign-up form.
        onNavigateBack = onDone,
        footer = {
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        },
    )
}

/** Six code boxes, a resend timer and a number pad; owns no state of its own. */
@Composable
private fun CodeEntryScreen(
    title: String,
    emoji: String,
    email: String,
    code: String,
    errorMessage: String?,
    secondsRemaining: Int,
    onTick: () -> Unit,
    onCodeChange: (String) -> Unit,
    onComplete: () -> Unit,
    onResend: () -> Unit,
    onNavigateBack: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    // Six boxes because the backend issues a six-digit code.
    val otpValues = List(OTP_LENGTH) { index -> code.getOrNull(index)?.toString() ?: "" }
    val remaining by rememberUpdatedState(secondsRemaining)

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            onTick()
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
        CodeEntryLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(horizontal = 24.dp),
            keypad = { keySize, rowSpacing ->
                NumberKeyboard(
                    onNumberClick = { num ->
                        if (code.length < OTP_LENGTH) {
                            val updated = code + num
                            onCodeChange(updated)
                            // Verify automatically once the code is complete.
                            if (updated.length == OTP_LENGTH) onComplete()
                        }
                    },
                    onDeleteClick = { onCodeChange(code.dropLast(1)) },
                    keySize = keySize,
                    rowSpacing = rowSpacing,
                )
            },
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Half the width beside the keypad, on a short screen: a smaller title that
            // wraps to two lines, and tighter gaps, so the code boxes stay in view.
            val sideBySide = rememberSideBySide()
            val titleSize = if (sideBySide) 26.sp else 32.sp
            val gap = if (sideBySide) 24.dp else 48.dp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = emoji,
                    fontSize = titleSize
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val emailText = buildAnnotatedString {
                append("We have sent a code to ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                    append(maskEmail(email))
                }
                append(". Enter it below to verify.")
            }

            Text(
                text = emailText,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(gap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                otpValues.forEach { value ->
                    OtpBox(
                        value = value,
                        isFocused = false, // Simple implementation without active focus tracking
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(gap))

            Text(
                text = "Didn't receive email?",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            AuthFieldError(errorMessage)

            Text(
                text = if (secondsRemaining > 0) "You can resend code in $secondsRemaining s" else "Tap to resend the code",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = secondsRemaining == 0, onClick = onResend),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            footer()
        }
    }
}

/**
 * The code, its explanation and a number pad. Stacked with the pad pinned to the bottom,
 * or, on a phone in landscape, the pad beside the code with smaller keys so all four
 * rows fit the short screen.
 */
@Composable
fun CodeEntryLayout(
    modifier: Modifier,
    keypad: @Composable (keySize: Dp, rowSpacing: Dp) -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    if (rememberSideBySide()) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            FillOrScrollColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                details()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                keypad(52.dp, 6.dp)
            }
        }
    } else {
        FillOrScrollColumn(modifier = modifier) {
            details()
            Spacer(modifier = Modifier.weight(1f))
            keypad(64.dp, 16.dp)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private const val OTP_LENGTH = 6

/** `ada@example.com` -> `a**@example.com`; never show the full address back. */
private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 1) return email
    return email.first() + "*".repeat((at - 1).coerceAtMost(6)) + email.substring(at)
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
            .background(AppTheme.colors.field, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (value.isNotEmpty()) AppTheme.colors.brand else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun NumberKeyboard(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 64.dp,
    rowSpacing: Dp = 16.dp,
) {
    val numbers = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "⌫")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        numbers.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { char ->
                    Box(
                        modifier = Modifier
                            .size(keySize)
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
