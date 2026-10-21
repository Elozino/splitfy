package com.elozino.splitfy.screens.account.appearance

import androidx.lifecycle.ViewModel
import com.elozino.splitfy.preferences.ThemePreferences
import com.elozino.splitfy.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** Account → App Appearance. Choosing a theme applies it at once, as a live preview. */
class AppearanceViewModel(private val preferences: ThemePreferences) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.mode

    fun selectTheme(mode: ThemeMode) = preferences.set(mode)
}

/** How each mode is named in the UI. */
val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.System -> "System default"
        ThemeMode.Light -> "Light"
        ThemeMode.Dark -> "Dark"
    }
