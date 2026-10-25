package com.elozino.splitfy.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * A header, such as a group's cover photo, that gives its height to the content under it
 * as that content scrolls up, and takes it back once the content is scrolled to the top.
 * For short windows, where a fixed header leaves the list no room.
 *
 * Attach [connection] with `Modifier.nestedScroll` to a parent of the scrolling content,
 * and size the header with [height].
 */
@Stable
class CollapsingHeaderState(private val maxHeightPx: Float) {
    var heightPx by mutableFloatStateOf(maxHeightPx)
        private set

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        // Scrolling up: shrink the header before the list moves.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            Offset(0f, apply(available.y, CollapseStep.BeforeContent))

        // Scrolling down: grow it back only with what the list didn't use, i.e. at the top.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            Offset(0f, apply(available.y, CollapseStep.AfterContent))
    }

    private fun apply(delta: Float, step: CollapseStep): Float {
        val result = collapseHeader(heightPx, maxHeightPx, delta, step)
        heightPx = result.heightPx
        return result.consumed
    }
}

/** When in a nested scroll a header gets its turn at a scroll [delta]. */
enum class CollapseStep { BeforeContent, AfterContent }

/** A header's new height after a scroll, and how much of the scroll it took. */
data class CollapseResult(val heightPx: Float, val consumed: Float)

/**
 * Applies a scroll [delta] (negative is the content moving up) to a header that is
 * [heightPx] tall and can be at most [maxHeightPx]. It collapses before the content
 * scrolls and expands only after, so scrolling down a long list doesn't reopen the
 * header until the list is back at its top.
 */
fun collapseHeader(heightPx: Float, maxHeightPx: Float, delta: Float, step: CollapseStep): CollapseResult {
    val applies = when (step) {
        CollapseStep.BeforeContent -> delta < 0f
        CollapseStep.AfterContent -> delta > 0f
    }
    if (!applies) return CollapseResult(heightPx, 0f)
    val newHeight = (heightPx + delta).coerceIn(0f, maxHeightPx)
    return CollapseResult(newHeight, newHeight - heightPx)
}

@Composable
fun rememberCollapsingHeaderState(maxHeight: Dp): CollapsingHeaderState {
    val maxPx = with(LocalDensity.current) { maxHeight.toPx() }
    return remember(maxPx) { CollapsingHeaderState(maxPx) }
}

/** The header's current height. */
@Composable
fun CollapsingHeaderState.currentHeight(): Dp = with(LocalDensity.current) { heightPx.toDp() }
