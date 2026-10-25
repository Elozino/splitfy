package com.elozino.splitfy.adaptive

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The layout Google's adaptive rules pick for real device sizes. Nothing here is our
 * own threshold: these pin down how we read Google's decisions, so an upgrade of the
 * adaptive library that changes them shows up as a failing test.
 */
class WindowLayoutTest {

    private fun layoutFor(widthDp: Int, heightDp: Int): WindowLayout = windowLayoutFor(
        // Computed through Google's breakpoints, as currentWindowAdaptiveInfo() does.
        WindowAdaptiveInfo(
            WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp.toFloat(), heightDp.toFloat()),
            Posture(),
        ),
        widthDp.dp,
        heightDp.dp,
    )

    @Test
    fun `a phone in portrait gets the bottom bar and one pane`() {
        val phone = layoutFor(widthDp = 411, heightDp = 891)
        assertFalse(phone.useNavigationRail)
        assertFalse(phone.useTwoPanes)
    }

    @Test
    fun `a phone on its side keeps the bottom bar but shows two panes`() {
        // Google keeps the bar while the window is short, and the width allows list and detail.
        val landscape = layoutFor(widthDp = 891, heightDp = 411)
        assertFalse(landscape.useNavigationRail)
        assertTrue(landscape.useTwoPanes)
    }

    @Test
    fun `an unfolded foldable in portrait gets a rail and one pane`() {
        val fold = layoutFor(widthDp = 673, heightDp = 841)
        assertTrue(fold.useNavigationRail)
        assertFalse(fold.useTwoPanes)
    }

    @Test
    fun `an unfolded foldable in landscape gets list and detail side by side`() {
        val fold = layoutFor(widthDp = 841, heightDp = 673)
        assertTrue(fold.useNavigationRail)
        assertTrue(fold.useTwoPanes)
    }

    @Test
    fun `a tablet gets a rail and two panes`() {
        val tablet = layoutFor(widthDp = 1280, heightDp = 800)
        assertTrue(tablet.useNavigationRail)
        assertTrue(tablet.useTwoPanes)
    }
}
