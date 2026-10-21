package com.elozino.splitfy.preferences

import com.elozino.splitfy.ui.theme.ThemeMode
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app theme the user picked, kept per device. Not tied to the account, and kept
 * across sign-out: it is a property of this phone, like its wallpaper.
 */
class ThemePreferences(private val settings: Settings) {

    private val _mode = MutableStateFlow(read())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        settings.putString(KEY, mode.name)
        _mode.value = mode
    }

    private fun read(): ThemeMode =
        settings.getStringOrNull(KEY)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.System

    private companion object {
        const val KEY = "theme.mode"
    }
}
