package com.elozino.splitfy.preferences

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity

/**
 * Tracks what is drawn behind the status bar, so its icons can stay readable: dark
 * on the brand-yellow Home header even in dark mode, light over the camera even in
 * light mode. Screens mark such areas with [statusBarBackground]; anywhere else the
 * theme's background is assumed.
 */
@Stable
class StatusBarAppearance {
    private class Area(val color: Color, val top: Float, val bottom: Float, val order: Long)

    private val areas = mutableStateMapOf<Any, Area>()
    private var nextOrder = 0L

    internal fun place(key: Any, color: Color, top: Float, bottom: Float) {
        val current = areas[key]
        if (current != null && current.color == color && current.top == top && current.bottom == bottom) return
        // Keep the order an area first got: the screen composed last (the one being
        // navigated to) wins while two overlap during a transition.
        areas[key] = Area(color, top, bottom, current?.order ?: nextOrder++)
    }

    internal fun remove(key: Any) {
        areas.remove(key)
    }

    /** The colour of the newest marked area that covers the status bar, if any. */
    internal fun colorBehind(statusBarHeight: Float): Color? {
        val bottomEdge = statusBarHeight.coerceAtLeast(1f)
        return areas.values
            .filter { it.top <= 0.5f && it.bottom >= bottomEdge }
            .maxByOrNull { it.order }
            ?.color
    }
}

val LocalStatusBarAppearance = staticCompositionLocalOf<StatusBarAppearance?> { null }

/**
 * Marks this element as drawing [color] behind the status bar while it reaches the
 * top of the screen (an app bar drawn under the bar, a full-screen camera). Has no
 * effect once it scrolls or moves away from the top.
 */
fun Modifier.statusBarBackground(color: Color): Modifier = composed {
    val appearance = LocalStatusBarAppearance.current ?: return@composed this
    val key = remember { Any() }
    DisposableEffect(appearance, key) { onDispose { appearance.remove(key) } }
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        appearance.place(key, color, bounds.top, bounds.bottom)
    }
}

/**
 * Whether the status bar should use dark icons: true over light colours. The
 * threshold is where black and white text have equal contrast, so the brand yellow
 * gets dark icons.
 */
@Composable
fun statusBarIconsDark(appearance: StatusBarAppearance): Boolean {
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val background = MaterialTheme.colorScheme.background
    val dark by remember(appearance, statusBarHeight, background) {
        derivedStateOf {
            (appearance.colorBehind(statusBarHeight) ?: background).luminance() > 0.179f
        }
    }
    return dark
}
