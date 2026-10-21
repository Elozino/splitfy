package com.elozino.splitfy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Skeletons stand in for content while it loads: grey shapes laid out like the real
 * screen, so the page doesn't jump when data arrives and nothing misleading (a "0.00"
 * balance, a "Group" title) shows in the meantime. Spinners are kept for actions in
 * progress (a button that was tapped), not for content that hasn't arrived yet.
 */

private val SkeletonBase = Color(0xFFECECEC)
private val SkeletonHighlight = Color(0xFFF6F6F6)

/** For placeholders drawn on the brand yellow: a translucent darkening instead of grey. */
private val AccentBase = Color.Black.copy(alpha = 0.10f)
private val AccentHighlight = Color.Black.copy(alpha = 0.04f)

/**
 * A soft highlight that sweeps left to right; every skeleton shares the same timing.
 * [onAccent] suits a coloured background such as the yellow home header.
 */
fun Modifier.shimmer(onAccent: Boolean = false): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    // Kept as State and read only while drawing: each frame redraws the gradient
    // without recomposing, which matters on a screen full of placeholders.
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerPhase",
    )
    val colors = if (onAccent) {
        listOf(AccentBase, AccentHighlight, AccentBase)
    } else {
        listOf(SkeletonBase, SkeletonHighlight, SkeletonBase)
    }
    drawBehind {
        // The highlight band is as wide as the shape and sweeps from just off its
        // left edge to just past its right edge.
        val span = size.width.coerceAtLeast(1f)
        val start = -span + phase.value * span * 3
        drawRect(Brush.linearGradient(colors, start = Offset(start, 0f), end = Offset(start + span, 0f)))
    }
}

/** One grey shape. Size it with [modifier]. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    onAccent: Boolean = false,
) {
    Box(modifier.clip(shape).shimmer(onAccent))
}

/** A line of text that hasn't loaded: [widthFraction] of the available width. */
@Composable
fun SkeletonText(widthFraction: Float = 1f, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    SkeletonBlock(modifier.fillMaxWidth(widthFraction).height(height), RoundedCornerShape(6.dp))
}

@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    SkeletonBlock(modifier.size(size), CircleShape)
}

/**
 * The common row: optional avatar, a title and subtitle, optional trailing amount.
 * Matches the app's list rows closely enough that real rows replace it in place.
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier,
    avatarSize: Dp? = 48.dp,
    titleFraction: Float = 0.55f,
    subtitleFraction: Float = 0.35f,
    trailing: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (avatarSize != null) {
            SkeletonCircle(avatarSize)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonText(titleFraction, height = 16.dp)
            SkeletonText(subtitleFraction, height = 12.dp)
        }
        if (trailing) {
            Spacer(Modifier.width(16.dp))
            SkeletonBlock(Modifier.width(72.dp).height(16.dp), RoundedCornerShape(6.dp))
        }
    }
}

/**
 * A column of placeholder rows, announced once to screen readers as "Loading".
 * Row widths vary a little so the list doesn't look like a barcode.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 6,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    spacing: Dp = 20.dp,
    item: @Composable (index: Int) -> Unit = { index ->
        SkeletonListItem(
            titleFraction = TITLE_WIDTHS[index % TITLE_WIDTHS.size],
            subtitleFraction = SUBTITLE_WIDTHS[index % SUBTITLE_WIDTHS.size],
        )
    },
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .clearAndSetSemantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(count) { item(it) }
    }
}

private val TITLE_WIDTHS = listOf(0.55f, 0.7f, 0.45f, 0.62f, 0.5f, 0.66f)
private val SUBTITLE_WIDTHS = listOf(0.35f, 0.28f, 0.4f, 0.3f, 0.38f, 0.25f)
