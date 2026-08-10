package com.elozino.splitify.core.domain.session

import com.elozino.splitify.core.data.storage.SessionStorage
import com.elozino.splitify.shared.api.dto.TokenPair
import com.elozino.splitify.shared.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the navigation graph branches on at start-up. */
sealed interface SessionState {
    /** Tokens are being read from storage; show the splash, not the login screen. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User?) : SessionState
}

/**
 * The single source of truth for "is someone signed in".
 *
 * The HTTP layer, the navigation graph and the account screen all observe this, so a
 * 401 anywhere in the app drops every screen back to sign-in exactly once.
 */
class SessionManager(private val storage: SessionStorage) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        _state.value = if (storage.readTokens() != null) {
            SessionState.SignedIn(user = null)
        } else {
            SessionState.SignedOut
        }
    }

    val isSignedIn: Boolean get() = _state.value is SessionState.SignedIn

    fun currentTokens(): TokenPair? = storage.readTokens()

    fun currentUserId(): String? = storage.readUserId()

    fun onAuthenticated(user: User, tokens: TokenPair) {
        storage.writeTokens(tokens)
        storage.writeUserId(user.id.value)
        _state.value = SessionState.SignedIn(user)
    }

    fun onTokensRefreshed(tokens: TokenPair) {
        storage.writeTokens(tokens)
    }

    fun onUserLoaded(user: User) {
        if (_state.value is SessionState.SignedIn) _state.value = SessionState.SignedIn(user)
    }

    /** Idempotent: repeated 401s during a burst of parallel requests sign out once. */
    fun signOut() {
        if (_state.value is SessionState.SignedOut) return
        storage.clear()
        _state.value = SessionState.SignedOut
    }
}
