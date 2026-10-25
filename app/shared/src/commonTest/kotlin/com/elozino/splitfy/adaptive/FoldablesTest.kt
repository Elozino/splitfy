package com.elozino.splitfy.adaptive

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Foldable postures as the Jetpack WindowManager reports them on a Pixel 9 Pro Fold
 * (inner screen 2076 × 2152 px, 852 × 883 dp) and a 7.6" foldable (673 × 841 dp).
 */
class FoldablesTest {

    private fun layoutFor(widthDp: Int, heightDp: Int, posture: Posture = Posture()): WindowLayout = windowLayoutFor(
        WindowAdaptiveInfo(
            WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp.toFloat(), heightDp.toFloat()),
            posture,
        ),
        widthDp.dp,
        heightDp.dp,
        // The Pixel 9 Pro Fold's 390 dpi.
        Density(2.4375f),
    )

    private fun halfOpen(bounds: Rect, vertical: Boolean, tabletop: Boolean = !vertical) = Posture(
        isTabletop = tabletop,
        hingeList = listOf(
            HingeInfo(bounds, isFlat = false, isVertical = vertical, isSeparating = true, isOccluding = false),
        ),
    )

    private fun flat(bounds: Rect, vertical: Boolean) = Posture(
        hingeList = listOf(
            HingeInfo(bounds, isFlat = true, isVertical = vertical, isSeparating = false, isOccluding = false),
        ),
    )

    private val verticalFold = Rect(1038f, 0f, 1038f, 2152f)
    private val horizontalFold = Rect(0f, 1038f, 2152f, 1038f)

    @Test
    fun `the folded cover screen is a phone`() {
        val cover = layoutFor(widthDp = 411, heightDp = 923)
        assertFalse(cover.useNavigationRail)
        assertFalse(cover.useTwoPanes)
        assertNull(cover.tabletopFold)
    }

    @Test
    fun `unfolded flat gets the rail and two panes and no tabletop fold`() {
        val open = layoutFor(widthDp = 852, heightDp = 883, posture = flat(verticalFold, vertical = true))
        assertTrue(open.useNavigationRail)
        assertTrue(open.useTwoPanes)
        assertNull(open.tabletopFold)
    }

    @Test
    fun `a medium-width foldable held like a book gets a pane each side of the hinge`() {
        // Google's directive alone gives this window one pane.
        assertFalse(layoutFor(widthDp = 673, heightDp = 841).useTwoPanes)
        val book = layoutFor(widthDp = 673, heightDp = 841, posture = halfOpen(verticalFold, vertical = true))
        assertTrue(book.useTwoPanes)
        assertNull(book.tabletopFold)
    }

    @Test
    fun `book posture keeps the hinge out of the panes`() {
        val info = WindowAdaptiveInfo(
            WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(673f, 841f),
            halfOpen(verticalFold, vertical = true),
        )
        val directive = paneScaffoldDirectiveFor(info)
        assertEquals(2, directive.maxHorizontalPartitions)
        assertEquals(listOf(verticalFold), directive.excludedBounds)
    }

    @Test
    fun `a flat foldable on a medium window keeps Google's single pane`() {
        val flatMedium = layoutFor(widthDp = 673, heightDp = 841, posture = flat(verticalFold, vertical = true))
        assertFalse(flatMedium.useTwoPanes)
    }

    @Test
    fun `tabletop reports the fold`() {
        val tabletop = layoutFor(widthDp = 883, heightDp = 852, posture = halfOpen(horizontalFold, vertical = false))
        assertEquals(horizontalFold, tabletop.tabletopFold)
        // Content gets the 426dp above the fold, not the whole 852dp window.
        assertEquals(1038f / 2.4375f, tabletop.usableHeight.value, absoluteTolerance = 0.01f)
    }

    @Test
    fun `a horizontal hinge lying flat is not tabletop`() {
        val lying = layoutFor(widthDp = 883, heightDp = 852, posture = flat(horizontalFold, vertical = false))
        assertNull(lying.tabletopFold)
        assertEquals(852.dp, lying.usableHeight)
    }

    @Test
    fun `in tabletop the bar fills the bottom half`() {
        assertEquals(1114, tabletopBarHeight(naturalHeight = 230, barBottom = 2152f, foldBottom = 1038f, maxHeight = 2152))
    }

    @Test
    fun `the bar never shrinks below its content or grows past the screen`() {
        // A bar whose bottom is above the fold (the screen sits in the top half).
        assertEquals(230, tabletopBarHeight(naturalHeight = 230, barBottom = 900f, foldBottom = 1038f, maxHeight = 2152))
        assertEquals(800, tabletopBarHeight(naturalHeight = 230, barBottom = 2152f, foldBottom = 1038f, maxHeight = 800))
    }

    @Test
    fun `an empty bar stays empty`() {
        assertEquals(0, tabletopBarHeight(naturalHeight = 0, barBottom = 2152f, foldBottom = 1038f, maxHeight = 2152))
    }
}
