package com.elozino.splitfy.preferences

import androidx.compose.runtime.Composable

/**
 * Matches the status bar's icons to the app theme. With a theme chosen in the app
 * rather than by the system, the platform would otherwise keep dark icons on a
 * dark screen (or the reverse).
 */
@Composable
expect fun SystemBarsEffect(darkTheme: Boolean)
