package com.elozino.splitfy.screens.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** A header that gives its height to the list under it on a short window. */
class CollapsingHeaderTest {

    private val max = 100f

    @Test
    fun `scrolling up shrinks the header before the list moves`() {
        assertEquals(CollapseResult(heightPx = 70f, consumed = -30f), collapseHeader(100f, max, -30f, CollapseStep.BeforeContent))
    }

    @Test
    fun `it takes only what it has left and passes the rest to the list`() {
        assertEquals(CollapseResult(heightPx = 0f, consumed = -20f), collapseHeader(20f, max, -50f, CollapseStep.BeforeContent))
        assertEquals(CollapseResult(heightPx = 0f, consumed = 0f), collapseHeader(0f, max, -50f, CollapseStep.BeforeContent))
    }

    @Test
    fun `scrolling down lets the list go first`() {
        // Before the list, a downward scroll is left alone, so a long list scrolls back first.
        assertEquals(CollapseResult(heightPx = 0f, consumed = 0f), collapseHeader(0f, max, 40f, CollapseStep.BeforeContent))
    }

    @Test
    fun `once the list is at its top the header grows back`() {
        assertEquals(CollapseResult(heightPx = 40f, consumed = 40f), collapseHeader(0f, max, 40f, CollapseStep.AfterContent))
    }

    @Test
    fun `it never grows past its full height`() {
        assertEquals(CollapseResult(heightPx = 100f, consumed = 10f), collapseHeader(90f, max, 60f, CollapseStep.AfterContent))
        assertEquals(CollapseResult(heightPx = 100f, consumed = 0f), collapseHeader(100f, max, 60f, CollapseStep.AfterContent))
    }

    @Test
    fun `leftover upward scroll after the list doesn't touch it`() {
        assertEquals(CollapseResult(heightPx = 50f, consumed = 0f), collapseHeader(50f, max, -10f, CollapseStep.AfterContent))
    }
}
