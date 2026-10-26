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
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset

/**
 * How the app lays itself out in the current window, decided by Google's Material 3
 * adaptive rules rather than by hand:
 *
 * - navigation: [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo] picks a
 *   bottom bar for compact widths and for short windows (a phone on its side), and a
 *   rail for medium and wider ones;
 * - panes: [calculatePaneScaffoldDirective] allows two side by side on expanded
 *   widths and accounts for hinges on foldables ([paneScaffoldDirectiveFor] adds
 *   two panes for a foldable held like a book);
 * - posture: where a half-open foldable standing on a table folds ([tabletopFold]).
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
    /**
     * The fold, in window pixels, while a foldable stands half open on a table; null
     * otherwise. Content goes above it and controls below ([TabletopBottomBar]).
     */
    val tabletopFold: Rect? = null,
    /**
     * The height content can use: the top half above the fold in tabletop posture, the
     * whole window otherwise. Size headers and images from this, not [height].
     */
    val usableHeight: Dp = height,
    /**
     * A vertical fold that splits the screen (a foldable held open like a book), in
     * window pixels; null otherwise. Single-column screens keep off it ([ReadableWidth]).
     */
    val bookFold: Rect? = null,
)

val LocalWindowLayout = staticCompositionLocalOf {
    WindowLayout(width = 360.dp, height = 800.dp, useNavigationRail = false, useTwoPanes = false)
}

/**
 * The layout Google's adaptive rules give for [info], in a window of [width] × [height].
 * [density] turns a tabletop fold's pixel bounds into [WindowLayout.usableHeight]; a
 * caller with no fold in [info] can leave it out.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun windowLayoutFor(info: WindowAdaptiveInfo, width: Dp, height: Dp, density: Density = Density(1f)): WindowLayout {
    val navigation = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(info)
    val fold = info.windowPosture.tabletopFold
    return WindowLayout(
        width = width,
        height = height,
        useNavigationRail = navigation.isRail(),
        useTwoPanes = paneScaffoldDirectiveFor(info).maxHorizontalPartitions > 1,
        tabletopFold = fold,
        usableHeight = fold?.let { with(density) { it.top.toDp() } }?.coerceAtMost(height) ?: height,
        bookFold = info.windowPosture.separatingVerticalHingeBounds.firstOrNull(),
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
        CompositionLocalProvider(LocalWindowLayout provides windowLayoutFor(info, maxWidth, maxHeight, LocalDensity.current)) {
            content()
        }
    }
}

/** Widest a single column of text, forms or settings should get. */
val ReadableMaxWidth = 720.dp

/**
 * Centres [content] in a column no wider than [ReadableMaxWidth], so forms and lists
 * don't stretch edge to edge on a tablet. On a phone it changes nothing.
 *
 * On a foldable held like a book, a centred column would run across the hinge, so it
 * moves to the larger side of the fold instead ([bookSide]), as Google advises.
 */
@Composable
fun ReadableWidth(content: @Composable () -> Unit) {
    val fold = LocalWindowLayout.current.bookFold
    var containerStart by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { containerStart = it.positionInWindow().x },
    ) {
        val width = constraints.maxWidth.toFloat()
        val side = fold?.let { bookSide(containerStart, width, it.left, it.right) } ?: (0f..width)
        val density = LocalDensity.current
        Box(
            Modifier
                .offset { IntOffset(side.start.roundToInt(), 0) }
                .width(with(density) { (side.endInclusive - side.start).toDp() })
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = ReadableMaxWidth).fillMaxHeight()) { content() }
        }
    }
}

/**
 * The part of a container (starting [containerStart] px from the window's left edge,
 * [containerWidth] px wide) a single column should use, in container pixels: all of
 * it, or the larger side of a hinge from [foldLeft] to [foldRight] (window pixels)
 * that crosses it.
 */
fun bookSide(containerStart: Float, containerWidth: Float, foldLeft: Float, foldRight: Float): ClosedFloatingPointRange<Float> {
    val hingeStart = foldLeft - containerStart
    val hingeEnd = foldRight - containerStart
    if (hingeEnd <= 0f || hingeStart >= containerWidth) return 0f..containerWidth
    val before = hingeStart.coerceAtLeast(0f)
    val after = (containerWidth - hingeEnd).coerceAtLeast(0f)
    return if (before >= after) 0f..before else hingeEnd.coerceAtMost(containerWidth)..containerWidth
}

/**
 * Fills the width on a phone; on a wider window, centres the element at
 * [ReadableMaxWidth]. For content inside a full-bleed background, such as Home's
 * yellow header, where wrapping the whole screen in [ReadableWidth] would shrink the
 * background too.
 */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadableMaxWidth)
