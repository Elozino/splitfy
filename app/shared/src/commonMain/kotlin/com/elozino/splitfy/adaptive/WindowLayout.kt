package com.elozino.splitfy.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the app lays itself out in the current window, decided by Google's Material 3
 * adaptive rules rather than by hand:
 *
 * - navigation: [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo] picks a
 *   bottom bar for compact widths and for short windows (a phone on its side), and a
 *   rail for medium and wider ones;
 * - panes: [calculatePaneScaffoldDirective] allows two side by side on expanded
 *   widths and accounts for hinges on foldables.
 *
 * [width] and [height] are the window's actual size, for the few places that scale
 * with it.
 */
@Immutable
data class WindowLayout(
    val width: Dp,
    val height: Dp,
    /** Show navigation as a side rail instead of a bottom bar. */
    val useNavigationRail: Boolean,
    /** Room for a list and its detail side by side. */
    val useTwoPanes: Boolean,
)

val LocalWindowLayout = staticCompositionLocalOf {
    WindowLayout(width = 360.dp, height = 800.dp, useNavigationRail = false, useTwoPanes = false)
}

/** The layout Google's adaptive rules give for [info], in a window of [width] × [height]. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun windowLayoutFor(info: WindowAdaptiveInfo, width: Dp, height: Dp): WindowLayout {
    val navigation = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(info)
    return WindowLayout(
        width = width,
        height = height,
        useNavigationRail = navigation.isRail(),
        useTwoPanes = calculatePaneScaffoldDirective(info).maxHorizontalPartitions > 1,
    )
}

/** Rails and drawers sit beside the content; bars sit under it. */
private fun NavigationSuiteType.isRail(): Boolean =
    this == NavigationSuiteType.NavigationRail || this == NavigationSuiteType.NavigationDrawer ||
        this == NavigationSuiteType.WideNavigationRailCollapsed || this == NavigationSuiteType.WideNavigationRailExpanded

/** Reads the window and provides [LocalWindowLayout] to [content]. */
@Composable
fun ProvideWindowLayout(content: @Composable () -> Unit) {
    val info = currentWindowAdaptiveInfo()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindowLayout provides windowLayoutFor(info, maxWidth, maxHeight)) {
            content()
        }
    }
}

/** Widest a single column of text, forms or settings should get. */
val ReadableMaxWidth = 720.dp

/**
 * Centres [content] in a column no wider than [ReadableMaxWidth], so forms and lists
 * don't stretch edge to edge on a tablet. On a phone it changes nothing.
 */
@Composable
fun ReadableWidth(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.widthIn(max = ReadableMaxWidth).fillMaxHeight()) { content() }
    }
}

/**
 * Fills the width on a phone; on a wider window, centres the element at
 * [ReadableMaxWidth]. For content inside a full-bleed background, such as Home's
 * yellow header, where wrapping the whole screen in [ReadableWidth] would shrink the
 * background too.
 */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadableMaxWidth)
