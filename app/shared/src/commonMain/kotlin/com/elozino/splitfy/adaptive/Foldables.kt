package com.elozino.splitfy.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Google's pane directive ([calculatePaneScaffoldDirective]), plus one rule from its
 * foldable guidance: a half-open foldable held like a book always gets two panes, one
 * each side of the hinge. Google's directive alone gives a medium-width window (a
 * 7.6" foldable's inner screen) a single pane, which the scaffold then squeezes into
 * the left half, leaving the right half blank.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun paneScaffoldDirectiveFor(info: WindowAdaptiveInfo): PaneScaffoldDirective {
    val google = calculatePaneScaffoldDirective(info)
    return if (google.maxHorizontalPartitions < 2 && info.windowPosture.isBook) {
        google.copy(maxHorizontalPartitions = 2, horizontalPartitionSpacerSize = 24.dp)
    } else {
        google
    }
}

/** Half open with the hinge running top to bottom, like a book. */
val Posture.isBook: Boolean get() = separatingVerticalHingeBounds.isNotEmpty()

/**
 * Where the fold is, in window pixels, while the device stands half open on a table
 * (tabletop posture): the hinge runs across the window and splits it into a top and a
 * bottom half. Null in every other posture.
 */
val Posture.tabletopFold: Rect?
    get() = if (isTabletop) separatingHorizontalHingeBounds.firstOrNull() else null

/**
 * How tall a bottom bar of [naturalHeight] px should be so that, in tabletop posture,
 * it fills the bottom half: from the fold's lower edge ([foldBottom]) to the bar's own
 * lower edge ([barBottom], both in window px). The screen's content then ends at the
 * fold. Never shorter than the bar's own content, never taller than [maxHeight]; a bar
 * with nothing in it stays empty.
 */
fun tabletopBarHeight(naturalHeight: Int, barBottom: Float, foldBottom: Float, maxHeight: Int): Int {
    if (naturalHeight == 0) return 0
    val belowFold = (barBottom - foldBottom).roundToInt()
    return belowFold.coerceAtLeast(naturalHeight).coerceAtMost(maxHeight)
}

/**
 * A screen's bottom action bar that follows Google's tabletop guidance: with the
 * device half open on a table, content stays in the top half and the controls take
 * the bottom half, so nothing sits on the fold. Wrap a Scaffold's `bottomBar` in it;
 * the bar's own content is centred in the bottom half. In every other posture it
 * changes nothing.
 */
@Composable
fun TabletopBottomBar(content: @Composable () -> Unit) {
    val fold = LocalWindowLayout.current.tabletopFold
    if (fold == null) {
        content()
        return
    }
    // The bar's lower edge stays put however tall it grows (a Scaffold anchors it to its
    // own bottom), so measuring it once places the top at the fold.
    var barBottom by remember { mutableStateOf<Float?>(null) }
    Layout(
        content = content,
        modifier = Modifier.onGloballyPositioned { barBottom = it.boundsInWindow().bottom },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val natural = placeables.maxOfOrNull { it.height } ?: 0
        val height = barBottom?.let { tabletopBarHeight(natural, it, fold.bottom, constraints.maxHeight) } ?: natural
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, height) {
            placeables.forEach { it.place(0, (height - it.height) / 2) }
        }
    }
}
