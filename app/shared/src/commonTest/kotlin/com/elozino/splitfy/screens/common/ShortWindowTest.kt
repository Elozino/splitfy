package com.elozino.splitfy.screens.common

import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When screens switch to their short-window arrangement, for real device sizes. */
class ShortWindowTest {

    @Test
    fun `the thresholds are Google's size class breakpoints`() {
        assertEquals(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND.dp, CompactHeightMax)
        assertEquals(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp, SideBySideMinWidth)
    }

    @Test
    fun `a phone in portrait keeps the stacked layout`() {
        assertFalse(useSideBySide(width = 411.dp, height = 891.dp))
        assertFalse(isCompactHeight(891.dp))
    }

    @Test
    fun `a phone on its side goes side by side`() {
        // A Pixel 9 in landscape, and the 720dp readable column it gets inside that.
        assertTrue(useSideBySide(width = 923.dp, height = 411.dp))
        assertTrue(useSideBySide(width = 720.dp, height = 411.dp))
        assertTrue(isCompactHeight(411.dp))
    }

    @Test
    fun `a small phone on its side still counts`() {
        assertTrue(useSideBySide(width = 640.dp, height = 360.dp))
    }

    @Test
    fun `tablets and unfolded foldables are tall enough to stay stacked`() {
        assertFalse(useSideBySide(width = 1280.dp, height = 800.dp)) // Pixel Tablet, landscape
        assertFalse(useSideBySide(width = 800.dp, height = 1280.dp)) // Pixel Tablet, portrait
        assertFalse(useSideBySide(width = 841.dp, height = 701.dp)) // Pixel Fold, open
    }

    @Test
    fun `a short but narrow window has no room to split`() {
        // A phone in split screen: short, and too narrow for two blocks.
        assertFalse(useSideBySide(width = 411.dp, height = 400.dp))
        assertTrue(isCompactHeight(400.dp))
    }

    @Test
    fun `the boundaries belong to the larger class`() {
        assertFalse(isCompactHeight(480.dp))
        assertTrue(isCompactHeight(479.dp))
        assertTrue(useSideBySide(width = 600.dp, height = 400.dp))
        assertFalse(useSideBySide(width = 599.dp, height = 400.dp))
    }

    @Test
    fun `action bars keep portrait's size and slim down on a short window`() {
        assertEquals(ActionBarMetrics(compact = false, verticalPadding = 24.dp, buttonHeight = 56.dp), actionBarMetrics(891.dp))
        assertEquals(ActionBarMetrics(compact = true, verticalPadding = 8.dp, buttonHeight = 48.dp), actionBarMetrics(411.dp))
    }

    @Test
    fun `slim action buttons are still a full touch target`() {
        assertTrue(actionBarMetrics(360.dp).buttonHeight >= 48.dp)
    }
}
