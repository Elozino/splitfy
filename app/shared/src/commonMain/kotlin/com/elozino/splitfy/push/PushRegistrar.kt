package com.elozino.splitfy.push

import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.SessionState
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the server's record of this device in step with the session.
 *
 * Registers whenever someone is signed in and the platform has a token — after
 * login, and again whenever FCM rotates the token. [unregister] runs before the
 * session is torn down on sign-out, while the request can still authenticate.
 */
class PushRegistrar(
    private val repository: PushRepository,
    private val session: SessionManager,
    private val settings: Settings,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(session.state, PushNotifications.token) { state, token ->
                if (state is SessionState.SignedIn) token else null
            }.distinctUntilChanged().collect { token ->
                // Keyed by user as well as token: after a forced sign-out (expired
                // session) the next account on this phone must register it again.
                if (token != null && marker(token) != settings.getStringOrNull(KEY_REGISTERED)) register(token)
            }
        }
    }

    private fun marker(token: String) = "${session.currentUserId().orEmpty()}|$token"

    private suspend fun register(token: String) {
        if (repository.register(token, pushPlatform) is DataResult.Success) {
            settings.putString(KEY_REGISTERED, marker(token))
        }
    }

    /** Best effort: a failure here must never keep the user signed in. */
    suspend fun unregister() {
        val token = settings.getStringOrNull(KEY_REGISTERED)?.substringAfter('|')
            ?: PushNotifications.token.value
            ?: return
        repository.unregister(token)
        settings.remove(KEY_REGISTERED)
    }

    private companion object {
        const val KEY_REGISTERED = "push.registered_token"
    }
}
