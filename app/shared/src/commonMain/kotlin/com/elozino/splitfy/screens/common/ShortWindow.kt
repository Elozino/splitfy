package com.elozino.splitfy.screens.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elozino.splitfy.adaptive.LocalWindowLayout

/** Google's compact height class ends here: anything shorter is a phone on its side. */
val CompactHeightMax = 480.dp

/** Google's medium width class starts here: room for two blocks across. */
val SideBySideMinWidth = 600.dp

/** A window under [CompactHeightMax] tall, where fixed-height heroes and covers should shrink. */
fun isCompactHeight(height: Dp): Boolean = height < CompactHeightMax

/**
 * Wide but short (a phone in landscape): put a hero or keypad beside the text instead of
 * above or below it, since stacking them would push the content off screen. Tablets and
 * unfolded foldables are tall enough to keep the portrait arrangement.
 */
fun useSideBySide(width: Dp, height: Dp): Boolean =
    width >= SideBySideMinWidth && isCompactHeight(height)

/**
 * How much room a screen's pinned bottom actions take. A short window can't spare the
 * ~105dp the portrait bar takes for one row of buttons, so it gets 8dp around 48dp
 * buttons (still Material's minimum touch target) instead of 24dp around 56dp.
 */
@Immutable
data class ActionBarMetrics(val compact: Boolean, val verticalPadding: Dp, val buttonHeight: Dp)

fun actionBarMetrics(height: Dp): ActionBarMetrics =
    if (isCompactHeight(height)) {
        ActionBarMetrics(compact = true, verticalPadding = 8.dp, buttonHeight = 48.dp)
    } else {
        ActionBarMetrics(compact = false, verticalPadding = 24.dp, buttonHeight = 56.dp)
    }

/** [actionBarMetrics] for the current window. */
@Composable
fun rememberActionBarMetrics(): ActionBarMetrics = actionBarMetrics(LocalWindowLayout.current.height)

/** [useSideBySide] for the current window. */
@Composable
fun rememberSideBySide(): Boolean = LocalWindowLayout.current.let { useSideBySide(it.width, it.height) }

/** [isCompactHeight] for the current window. */
@Composable
fun rememberCompactHeight(): Boolean = isCompactHeight(LocalWindowLayout.current.height)

/**
 * A column that fills the screen when it fits, so `Modifier.weight` spacers still pin
 * buttons to the bottom in portrait, and scrolls when it doesn't: in landscape, or once
 * the keyboard takes half the screen. Sits above the keyboard, so the focused field and
 * the button under it stay reachable.
 *
 * Callers inside a Scaffold should pass `Modifier.padding(padding).consumeWindowInsets(padding)`
 * so the navigation bar isn't counted twice under the keyboard.
 */
@Composable
fun FillOrScrollColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.imePadding()) {
        Column(
            // Under verticalScroll the column has no maximum height, so weights share out
            // the minimum instead: the viewport's height.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .heightIn(min = if (constraints.hasBoundedHeight) maxHeight else 0.dp),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
