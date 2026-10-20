package com.elozino.splitfy.invites

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareInvite(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Join my group on Splitfy: $url")
            }, "Invite to group"))
        }
    }
}
