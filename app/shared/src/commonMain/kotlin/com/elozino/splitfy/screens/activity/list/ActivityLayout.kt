package com.elozino.splitfy.screens.activity.list

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * The filter pills share the row only while it is phone-narrow: Google's compact width
 * class. Wider, three equal thirds of a tablet column read as bars, not filters.
 */
internal fun activityFiltersFillWidth(available: Dp): Boolean =
    available.value < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND

/** Each pill's width when they don't fill the row; enough that "All" isn't a stub. */
internal val ActivityFilterMinWidth = 112.dp
