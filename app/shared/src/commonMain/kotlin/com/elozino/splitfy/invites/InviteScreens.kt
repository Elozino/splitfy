package com.elozino.splitfy.invites

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.GroupInvite
import com.elozino.splitfy.api.dto.InvitePreview
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun InvitePreviewDialog(token: String, signedIn: Boolean, onSignIn: () -> Unit, onJoined: (String) -> Unit) {
    val repository: InviteRepository = koinInject()
    var preview by remember(token) { mutableStateOf<InvitePreview?>(null) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var busy by remember(token) { mutableStateOf(false) }
    var attempt by remember(token) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token, attempt) {
        error = null
        when (val result = repository.preview(token)) {
            is DataResult.Success -> preview = result.data
            is DataResult.Failure -> error = result.error.userMessage()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) InviteLinks.clear() },
        title = { Text(preview?.groupName ?: "Group invitation") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            preview?.let { Text("${it.invitedBy} invited you to join ${it.memberCount} members on Splitfy.") }
            if (preview == null && error == null) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        dismissButton = { TextButton(onClick = { InviteLinks.clear() }, enabled = !busy) { Text("Not now") } },
        confirmButton = {
            if (preview == null) {
                TextButton(onClick = { attempt++ }, enabled = error != null) { Text("Retry") }
            } else Button(enabled = !busy, onClick = {
                if (!signedIn) onSignIn() else scope.launch {
                    busy = true
                    error = null
                    when (val result = repository.accept(token)) {
                        is DataResult.Success -> { InviteLinks.clear(); onJoined(result.data.id.value) }
                        is DataResult.Failure -> error = result.error.userMessage()
                    }
                    busy = false
                }
            }) { Text(if (busy) "Joining…" else if (signedIn) "Join group" else "Sign in to join") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareInviteScreen(groupId: String, onBack: () -> Unit) {
    val repository: InviteRepository = koinInject()
    val groups: com.elozino.splitfy.repositories.GroupRepository = koinInject()
    val session: com.elozino.splitfy.SessionManager = koinInject()
    var invite by remember(groupId) { mutableStateOf<GroupInvite?>(null) }
    var error by remember(groupId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var canReset by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val share = rememberShareInvite()
    LaunchedEffect(groupId, attempt) {
        busy = true
        error = null
        when (val result = repository.current(groupId)) {
            is DataResult.Success -> invite = result.data
            is DataResult.Failure -> error = result.error.userMessage()
        }
        val group = groups.get(groupId)
        if (group is DataResult.Success) {
            val role = group.data.members.firstOrNull { it.userId.value == session.currentUserId() }?.role
            canReset = role == com.elozino.splitfy.domain.model.GroupRole.OWNER || role == com.elozino.splitfy.domain.model.GroupRole.ADMIN
        }
        busy = false
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Invite to group") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Anyone with this link can join your group. Share it only with people you want to invite.")
            if (busy) CircularProgressIndicator()
            invite?.let { link ->
                Text(link.url)
                Text("The link expires after 30 days.")
                Button(onClick = { share(link.url) }, enabled = !busy) { Text("Share invite link") }
                if (canReset) TextButton(onClick = { confirmReset = true }, enabled = !busy) { Text("Reset invite link") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (error != null && !busy) TextButton(onClick = { attempt++ }) { Text("Retry") }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset invite link?") },
        text = { Text("The old link will stop working. Existing members will stay in the group.") },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            confirmReset = false
            scope.launch {
                busy = true
                error = null
                when (val result = repository.reset(groupId)) {
                    is DataResult.Success -> invite = result.data
                    is DataResult.Failure -> error = result.error.userMessage()
                }
                busy = false
            }
        }) { Text("Reset link") } },
    )
}
