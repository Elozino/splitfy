package com.elozino.splitfy.screens.account.security_settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.api.dto.TwoFactorSetupResponse
import com.elozino.splitfy.ui.components.AuthFieldError
import com.elozino.splitfy.ui.components.LoadErrorState
import com.elozino.splitfy.ui.components.PrimaryButton
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Turning on two-step verification: add splitfy to an authenticator app (QR code,
 * a link that opens the app, or the key typed by hand), confirm with one code, then
 * save the recovery codes, which are shown only here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: TwoFactorSetupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Two-step verification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // Once codes are showing, leaving goes through "Done" so they are not skipped by accident.
                    if (!state.isEnabled) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        val setup = state.setup
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isEnabled -> RecoveryCodesStep(state.recoveryCodes, snackbar, onDone = onNavigateBack)
                setup != null -> AddToAppStep(
                    setup = setup,
                    state = state,
                    snackbar = snackbar,
                    onCodeChange = viewModel::onCodeChange,
                    onConfirm = viewModel::confirm,
                )
                state.loadError != null -> LoadErrorState(message = state.loadError!!, onRetry = viewModel::start)
                else -> Unit // loading: the request is quick, and a spinner would only flash
            }
        }
    }
}

@Composable
private fun AddToAppStep(
    setup: TwoFactorSetupResponse,
    state: TwoFactorSetupState,
    snackbar: SnackbarHostState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "1. Scan this code with an authenticator app such as Google Authenticator, Microsoft Authenticator or 1Password.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(220.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberQrCodePainter(setup.otpauthUri),
                contentDescription = "QR code to add splitfy to your authenticator app",
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            // On the phone that has the authenticator, opening the link is easier than scanning.
            val opened = runCatching { uriHandler.openUri(setup.otpauthUri) }.isSuccess
            if (!opened) scope.launch { snackbar.showSnackbar("No authenticator app found. Scan the code or enter the key instead.") }
        }) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in authenticator app")
        }

        Spacer(Modifier.height(16.dp))
        Text("Or enter this key by hand:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .clickable {
                    clipboard.setText(AnnotatedString(setup.secret))
                    scope.launch { snackbar.showSnackbar("Key copied") }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                setup.secret.chunked(4).joinToString(" "),
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
            )
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy key")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "2. Enter the 6-digit code the app shows for splitfy.",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("123456") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        AuthFieldError(state.errorMessage)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = if (state.isSubmitting) "Checking…" else "Turn on",
            onClick = onConfirm,
            enabled = state.canConfirm,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecoveryCodesStep(codes: List<String>, snackbar: SnackbarHostState, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var saved by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Two-step verification is on", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Save these recovery codes somewhere safe, like a password manager. If you lose your phone, " +
                "each one lets you sign in once. You won't be able to see them again.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            codes.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { code ->
                        Text(code, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                clipboard.setText(AnnotatedString(codes.joinToString("\n")))
                scope.launch { snackbar.showSnackbar("Recovery codes copied") }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Copy all")
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable { saved = !saved },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Text("I've saved my recovery codes")
        }
        PrimaryButton(text = "Done", onClick = onDone, enabled = saved)
        Spacer(Modifier.height(24.dp))
    }
}
