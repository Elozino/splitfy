package com.elozino.splitfy.screens.account.linked_accounts

import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.screens.auth.shared.authOption
import com.elozino.splitfy.social.SocialSignInConfig
import com.elozino.splitfy.social.rememberSocialSignIn
import com.elozino.splitfy.ui.components.LoadErrorState
import com.elozino.splitfy.ui.generated.resources.UiRes
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedAccountsScreen(
    onNavigateBack: () -> Unit,
    viewModel: LinkedAccountsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val socialSignIn = rememberSocialSignIn(koinInject<SocialSignInConfig>())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Linked Accounts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val loadError = state.loadError
        when {
            state.isLoading && state.linked == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = AppTheme.colors.brand)
            }
            loadError != null -> LoadErrorState(loadError, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(
                    "Sign in to splitfy with these accounts as well as your email.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                val providers = state.providers(socialSignIn.providers)
                if (providers.isEmpty()) {
                    Text("No sign-in providers are available on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
                providers.forEach { provider ->
                    ProviderRow(
                        provider = provider,
                        linkedEmail = state.identity(provider)?.let { it.email ?: "Connected" },
                        isBusy = state.busyProvider == provider,
                        enabled = state.busyProvider == null,
                        canLink = state.canLink(provider, socialSignIn.providers),
                        onLink = {
                            viewModel.onLinkStarted(provider)
                            scope.launch { viewModel.onSignInResult(provider, socialSignIn.signIn(provider)) }
                        },
                        onUnlink = { viewModel.askToUnlink(provider) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (state.linked?.hasPassword == false) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Your account has no password, so you can't disconnect your only linked account. " +
                            "Use \"Forgot password\" on the sign-in screen to set one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }

    state.confirmUnlink?.let { provider ->
        val name = provider.displayName
        AlertDialog(
            onDismissRequest = viewModel::dismissUnlink,
            title = { Text("Disconnect $name?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (state.canUnlink(provider)) "You won't be able to sign in with $name any more."
                    else "$name is the only way into this account. Set a password or connect another account first."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.unlink(provider) }, enabled = state.canUnlink(provider)) {
                    Text("Disconnect", color = AppTheme.colors.negative, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissUnlink) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun ProviderRow(
    provider: OAuthProvider,
    linkedEmail: String?,
    isBusy: Boolean,
    enabled: Boolean,
    canLink: Boolean,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = UiRes.getUri(provider.authOption().iconUri),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(provider.displayName, fontWeight = FontWeight.Bold)
            Text(linkedEmail ?: "Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        when {
            isBusy -> CircularProgressIndicator(color = AppTheme.colors.brand, modifier = Modifier.size(24.dp))
            linkedEmail != null -> TextButton(onClick = onUnlink, enabled = enabled) {
                Text("Disconnect", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            canLink -> TextButton(onClick = onLink, enabled = enabled) {
                Text("Connect", color = AppTheme.colors.brand, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val OAuthProvider.displayName: String
    get() = when (this) {
        OAuthProvider.GOOGLE -> "Google"
        OAuthProvider.APPLE -> "Apple"
    }
