package com.elozino.splitfy.preferences

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBarsEffect(darkTheme: Boolean) {
    val context = LocalContext.current
    SideEffect {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        val window = (current as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
