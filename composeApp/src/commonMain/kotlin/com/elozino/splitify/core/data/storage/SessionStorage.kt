package com.elozino.splitify.core.data.storage

import com.elozino.splitify.shared.api.dto.TokenPair
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * Persists the session across launches.
 *
 * On iOS this is backed by the Keychain. On Android it is SharedPreferences, which
 * is private to the app but not encrypted at rest — moving it to the Android
 * Keystore is tracked as follow-up work (see docs/ARCHITECTURE.md).
 */
class SessionStorage(private val settings: Settings) {

    fun readTokens(): TokenPair? {
        val access = settings.getStringOrNull(KEY_ACCESS_TOKEN) ?: return null
        val refresh = settings.getStringOrNull(KEY_REFRESH_TOKEN) ?: return null
        return TokenPair(
            accessToken = access,
            refreshToken = refresh,
            expiresInSeconds = settings.getLong(KEY_EXPIRES_IN, 0L),
        )
    }

    fun writeTokens(tokens: TokenPair) {
        settings[KEY_ACCESS_TOKEN] = tokens.accessToken
        settings[KEY_REFRESH_TOKEN] = tokens.refreshToken
        settings[KEY_EXPIRES_IN] = tokens.expiresInSeconds
    }

    fun readUserId(): String? = settings.getStringOrNull(KEY_USER_ID)

    fun writeUserId(userId: String) {
        settings[KEY_USER_ID] = userId
    }

    /** Called on sign-out and whenever a refresh definitively fails. */
    fun clear() {
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
        settings.remove(KEY_EXPIRES_IN)
        settings.remove(KEY_USER_ID)
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "session.access_token"
        const val KEY_REFRESH_TOKEN = "session.refresh_token"
        const val KEY_EXPIRES_IN = "session.expires_in"
        const val KEY_USER_ID = "session.user_id"
    }
}
