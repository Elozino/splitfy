package com.elozino.splitfy.screens.home.main

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elozino.splitfy.adaptive.WindowLayout

/**
 * Home's summary (the yellow balance header) sits beside the groups list, like a
 * supporting pane, wherever Google's rules allow two panes: a tablet or unfolded
 * foldable on its side, and a phone on its side, where a header across the top would
 * leave the short window little room for the list. Otherwise it stays on top.
 */
internal fun homeSummaryBesideList(layout: WindowLayout): Boolean = layout.useTwoPanes

/** The summary pane's width beside the list: about two fifths of the window, within reason. */
internal fun homeSummaryWidth(windowWidth: Dp): Dp = (windowWidth * 0.38f).coerceIn(320.dp, 420.dp)
