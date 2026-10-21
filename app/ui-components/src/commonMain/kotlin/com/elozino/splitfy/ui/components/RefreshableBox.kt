package com.elozino.splitfy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elozino.splitfy.ui.theme.primaryLight

/**
 * Swipe down to refresh, in the app's colours.
 *
 * The pull gesture needs a scrollable child to report overscroll. Screens here mix
 * lazy lists with plain columns and centred empty or error states, so [content] sits
 * in a scroll container holding exactly one screen of content: it has nothing to scroll itself,
 * but passes a downward drag up to the refresh box, and a lazy list inside it keeps
 * scrolling normally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /** The Scaffold's padding, so the spinner appears below the top bar rather than behind it. */
    indicatorPadding: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorPadding.calculateTopPadding()),
                containerColor = primaryLight,
                color = Color.Black,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The scroll container measures its child with unbounded height, so the
            // child is pinned to one screen explicitly. Sizing the container instead
            // left content unbounded: weighted sections collapsed to nothing and a
            // LazyColumn inside crashed on the infinite height.
            val screenHeight = maxHeight
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(screenHeight), content = content)
            }
        }
    }
}
