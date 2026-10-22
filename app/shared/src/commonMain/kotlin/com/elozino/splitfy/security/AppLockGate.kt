package com.elozino.splitfy.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.SessionState
import com.elozino.splitfy.ui.components.PrimaryButton
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Draws [content] and, while [AppLockController.locked], a full-screen lock over it
 * that only Face ID / Touch ID / fingerprint (or the device passcode) removes.
 *
 * The app underneath stays composed so unlocking returns the user exactly where
 * they were, rather than rebuilding the navigation stack.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val controller: AppLockController = koinInject()
    val session: SessionManager = koinInject()
    val locked by controller.locked.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { controller.onForeground() }

    Box(Modifier.fillMaxSize()) {
        content()
        if (locked && sessionState is SessionState.SignedIn) {
            // Back must not reach the screens underneath.
            BackHandler(enabled = true) {}
            LockScreen(onUnlocked = controller::unlock)
        }
    }
}

@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    val authenticator = rememberDeviceAuthenticator()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }

    fun unlock() {
        if (prompting) return
        // No biometrics and no passcode any more (the user removed them): there is
        // nothing to check against, and a lock nobody can open is worse than none.
        if (authenticator.availability() == DeviceAuthAvailability.Unavailable) {
            onUnlocked()
            return
        }
        prompting = true
        scope.launch {
            when (val result = authenticator.authenticate("Confirm it's you to open Splitfy")) {
                DeviceAuthResult.Success -> onUnlocked()
                DeviceAuthResult.Cancelled -> message = null
                is DeviceAuthResult.Failed -> message = result.message
            }
            prompting = false
        }
    }

    LaunchedEffect(Unit) { unlock() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text("Splitfy is locked", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                message ?: "Unlock to continue where you left off.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton(text = "Unlock", onClick = ::unlock, enabled = !prompting)
        }
    }
}
