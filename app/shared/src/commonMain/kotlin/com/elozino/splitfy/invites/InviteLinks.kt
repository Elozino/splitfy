package com.elozino.splitfy.invites

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Platform entry points keep a link until the user explicitly joins or dismisses it. */
object InviteLinks {
    private var storage: com.russhwolf.settings.Settings? = null
    private const val KEY = "invites.pending_token"

    /** Bind after platform storage is available, preserving a cold-start URL received first. */
    fun bind(settings: com.russhwolf.settings.Settings) {
        storage = settings
        if (pending.value == null) pending.value = settings.getStringOrNull(KEY)
        pending.value?.let { settings.putString(KEY, it) }
    }

    private val pending = MutableStateFlow<String?>(null)
    val token = pending.asStateFlow()

    fun open(url: String) {
        parse(url)?.let { pending.value = it; storage?.putString(KEY, it) }
    }

    fun clear() { pending.value = null; storage?.remove(KEY) }

    fun parse(url: String): String? {
        val match = Regex("^(?:https://splitfy\\.app/i/|splitfy://invite/)([A-Za-z0-9_-]{22})(?:[?#].*)?$")
            .matchEntire(url) ?: return null
        return match.groupValues[1]
    }
}

@Composable
expect fun rememberShareInvite(): (String) -> Unit
