package com.elozino.splitfy.screens.account.security_settings

import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.api.dto.DevicePlatform
import com.elozino.splitfy.api.dto.DeviceSession
import com.elozino.splitfy.ui.components.LoadErrorState
import com.elozino.splitfy.ui.components.RefreshableBox
import com.elozino.splitfy.ui.components.SkeletonList
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel

/** Every device signed in to the account; any but this one can be signed out from here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onNavigateBack: () -> Unit,
    viewModel: DevicesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    state.pendingRevoke?.let { session ->
        ConfirmDialog(
            title = "Sign out ${session.displayName}?",
            body = "It will need to sign in again. Anything already open there may keep working for up to 15 minutes.",
            confirm = "Sign out",
            isWorking = state.isWorking,
            onConfirm = viewModel::confirmRevoke,
            onDismiss = { viewModel.askToRevoke(null) },
        )
    }
    if (state.isConfirmingRevokeOthers) {
        ConfirmDialog(
            title = "Sign out of all other devices?",
            body = "Every device except this one will need to sign in again.",
            confirm = "Sign out all",
            isWorking = state.isWorking,
            onConfirm = viewModel::confirmRevokeOthers,
            onDismiss = { viewModel.askToRevokeOthers(false) },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Devices", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when {
            state.isLoading -> SkeletonList(modifier = Modifier.padding(padding), count = 3)
            state.loadError != null && state.sessions.isEmpty() -> LoadErrorState(
                message = state.loadError!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            else -> RefreshableBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, indicatorPadding = padding) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                    state.current?.let { current ->
                        item { SectionLabel("This device") }
                        item { SessionRow(current, onSignOut = null) }
                    }
                    if (state.others.isNotEmpty()) {
                        item { SectionLabel("Other devices") }
                        items(state.others, key = { it.id }) { session ->
                            SessionRow(session, onSignOut = { viewModel.askToRevoke(session) })
                            HorizontalDivider()
                        }
                        item {
                            OutlinedButton(
                                onClick = { viewModel.askToRevokeOthers(true) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sign out of all other devices")
                            }
                        }
                    } else {
                        item {
                            Text(
                                "You're not signed in anywhere else.",
                                modifier = Modifier.padding(vertical = 24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SessionRow(session: DeviceSession, onSignOut: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (session.platform) {
                DevicePlatform.IOS -> Icons.Default.PhoneIphone
                DevicePlatform.ANDROID -> Icons.Default.PhoneAndroid
                null -> Icons.Default.Smartphone
            },
            contentDescription = null,
            tint = AppTheme.colors.brand,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(session.displayName, fontWeight = FontWeight.Medium)
            Text(
                if (session.current) "Active now" else "Last active ${relativeTime(session.lastUsedAt)}",
                fontSize = 13.sp,
                color = if (session.current) AppTheme.colors.brand else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Signed in ${relativeTime(session.createdAt)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onSignOut != null) {
            TextButton(onClick = onSignOut) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** "just now", "5 minutes ago", "3 days ago": the precision a session list needs. */
internal fun relativeTime(instant: Instant, now: Instant = Clock.System.now()): String {
    val elapsed = now - instant
    val minutes = elapsed.inWholeMinutes
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> plural(minutes, "minute")
        elapsed.inWholeHours < 24 -> plural(elapsed.inWholeHours, "hour")
        elapsed.inWholeDays < 30 -> plural(elapsed.inWholeDays, "day")
        else -> plural(elapsed.inWholeDays / 30, "month")
    }
}

private fun plural(count: Long, unit: String) = if (count == 1L) "1 $unit ago" else "$count ${unit}s ago"

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    isWorking: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isWorking,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(confirm) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancel") } },
    )
}
