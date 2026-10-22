package com.elozino.splitfy.screens.auth.two_factor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.screens.auth.otp.NumberKeyboard
import com.elozino.splitfy.screens.auth.otp.OtpBox
import com.elozino.splitfy.screens.auth.shared.AuthViewModel
import com.elozino.splitfy.ui.components.AuthFieldError
import com.elozino.splitfy.ui.components.AuthTextField
import com.elozino.splitfy.ui.components.PrimaryButton

/**
 * Second sign-in step for accounts with an authenticator app: six digits, or a
 * recovery code for someone who has lost the phone. Reached from both the password
 * and the Google/Apple sign-in.
 */
@Composable
fun TwoFactorSignInScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onSignedIn()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                IconButton(
                    onClick = {
                        viewModel.cancelTwoFactor()
                        onNavigateBack()
                    },
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Two-step verification", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                if (state.useRecoveryCode) {
                    "Enter one of the recovery codes you saved when you turned on two-step verification. Each code works once."
                } else {
                    "Open your authenticator app and enter the 6-digit code for Splitfy."
                },
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            if (state.useRecoveryCode) {
                AuthTextField(
                    value = state.twoFactorCode,
                    onValueChange = viewModel::onTwoFactorCodeChange,
                    label = "Recovery code",
                    placeholder = "xxxxx-xxxxx",
                    leadingIcon = Icons.Default.Key,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(6) { index ->
                        OtpBox(
                            value = state.twoFactorCode.getOrNull(index)?.toString().orEmpty(),
                            isFocused = false,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            AuthFieldError(state.errorMessage)

            TextButton(onClick = viewModel::toggleRecoveryCode, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.useRecoveryCode) "Use your authenticator app instead" else "Lost your phone? Use a recovery code")
            }

            Spacer(Modifier.weight(1f))

            if (state.useRecoveryCode) {
                PrimaryButton(
                    text = if (state.isSubmitting) "Checking…" else "Verify",
                    onClick = viewModel::verifyTwoFactor,
                    enabled = state.canSubmitTwoFactor,
                )
            } else {
                NumberKeyboard(
                    onNumberClick = { digit ->
                        if (state.isSubmitting) return@NumberKeyboard
                        val updated = state.twoFactorCode + digit
                        viewModel.onTwoFactorCodeChange(updated)
                        // Verify as soon as the sixth digit lands, as the email-code screens do.
                        if (updated.length == 6) viewModel.verifyTwoFactor()
                    },
                    onDeleteClick = { viewModel.onTwoFactorCodeChange(state.twoFactorCode.dropLast(1)) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
