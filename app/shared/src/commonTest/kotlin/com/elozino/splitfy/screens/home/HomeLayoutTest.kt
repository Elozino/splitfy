package com.elozino.splitfy.screens.home

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.elozino.splitfy.adaptive.WindowLayout
import com.elozino.splitfy.adaptive.windowLayoutFor
import com.elozino.splitfy.screens.activity.list.activityFiltersFillWidth
import com.elozino.splitfy.screens.home.main.homeSummaryBesideList
import com.elozino.splitfy.screens.home.main.homeSummaryWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Home and Activity on real device sizes, through Google's size classes. */
class HomeLayoutTest {

    private fun layoutFor(widthDp: Int, heightDp: Int): WindowLayout = windowLayoutFor(
        WindowAdaptiveInfo(
            WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp.toFloat(), heightDp.toFloat()),
            Posture(),
        ),
        widthDp.dp,
        heightDp.dp,
    )

    @Test
    fun `a phone in portrait keeps the summary on top`() {
        assertFalse(homeSummaryBesideList(layoutFor(widthDp = 411, heightDp = 891)))
    }

    @Test
    fun `a tablet in portrait keeps the summary on top`() {
        assertFalse(homeSummaryBesideList(layoutFor(widthDp = 800, heightDp = 1280)))
    }

    @Test
    fun `a tablet in landscape puts the summary beside the groups`() {
        assertTrue(homeSummaryBesideList(layoutFor(widthDp = 1280, heightDp = 800)))
    }

    @Test
    fun `a phone on its side puts the summary beside the groups`() {
        assertTrue(homeSummaryBesideList(layoutFor(widthDp = 891, heightDp = 411)))
    }

    @Test
    fun `the summary pane stays between a phone's width and a list pane's`() {
        assertEquals(338.58.dp.value, homeSummaryWidth(891.dp).value, 0.01f)
        assertEquals(320.dp, homeSummaryWidth(840.dp))
        assertEquals(420.dp, homeSummaryWidth(1280.dp))
    }

    @Test
    fun `activity filters fill the row only at phone widths`() {
        assertTrue(activityFiltersFillWidth(411.dp))
        assertFalse(activityFiltersFillWidth(600.dp))
        assertFalse(activityFiltersFillWidth(720.dp))
    }
}
