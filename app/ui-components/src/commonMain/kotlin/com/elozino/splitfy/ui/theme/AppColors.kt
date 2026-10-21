package com.elozino.splitfy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours with a meaning in this app that Material's scheme has no slot for.
 *
 * Use [MaterialTheme.colorScheme] for surfaces and text, and these for money and
 * brand: green for what you're owed, red for what you owe, the brand yellow for
 * primary actions. Each has a light and a dark value tuned for contrast on that
 * theme's background. See docs/THEMING.md for which to use where.
 */
@Immutable
data class AppColors(
    /** The brand yellow: primary buttons, the home header, selected tabs. Same in both themes. */
    val brand: Color,
    /** Text and icons drawn on [brand]. Always dark: yellow needs dark content in either theme. */
    val onBrand: Color,
    /** Money owed to you, "settled", success. */
    val positive: Color,
    /** Money you owe, destructive actions. */
    val negative: Color,
    /** Raised fields, chips and cards that sit on the background (search boxes, summary cards). */
    val field: Color,
    /** Placeholder shapes while content loads. */
    val skeletonBase: Color,
    val skeletonHighlight: Color,
)

val LightAppColors = AppColors(
    brand = Color(0xFFFEBB1B),
    onBrand = Color(0xFF212121),
    positive = Color(0xFF2E7D32),
    negative = Color(0xFFC62828),
    field = Color(0xFFF5F5F5),
    skeletonBase = Color(0xFFECECEC),
    skeletonHighlight = Color(0xFFF6F6F6),
)

val DarkAppColors = AppColors(
    brand = Color(0xFFFEBB1B),
    onBrand = Color(0xFF212121),
    positive = Color(0xFF81C784),
    negative = Color(0xFFEF9A9A),
    field = Color(0xFF2A2824),
    skeletonBase = Color(0xFF2C2A26),
    skeletonHighlight = Color(0xFF3A3833),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** `AppTheme.colors.positive` and friends, inside an [AppTheme]. */
object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
