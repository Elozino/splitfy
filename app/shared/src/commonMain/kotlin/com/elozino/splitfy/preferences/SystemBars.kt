package com.elozino.splitfy.preferences

import androidx.compose.runtime.Composable

/**
 * Sets the system bars' icon colours. With a theme chosen in the app rather than by
 * the system, the platform would otherwise keep dark icons on a dark screen (or the
 * reverse).
 *
 * [darkStatusBarIcons] follows what is drawn behind the status bar (see
 * [StatusBarAppearance]); the navigation bar and system sheets follow [darkTheme].
 */
@Composable
expect fun SystemBarsEffect(darkTheme: Boolean, darkStatusBarIcons: Boolean = !darkTheme)
