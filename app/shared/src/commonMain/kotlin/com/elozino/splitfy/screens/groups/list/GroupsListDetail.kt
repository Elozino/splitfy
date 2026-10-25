package com.elozino.splitfy.screens.groups.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elozino.splitfy.screens.groups.detail.main.GroupDetailScreen
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Groups as Google's list-detail canonical layout: on a wide window the list and the
 * selected group side by side, the way a tablet mail app shows the inbox and a
 * message. [ListDetailPaneScaffold] sizes the panes, keeps them clear of a foldable's
 * hinge and animates between them; Back closes the open group before leaving the screen.
 * (Google's NavigableListDetailPaneScaffold does the Back part too, but it isn't in the
 * multiplatform API yet, so it's wired here the same way.)
 *
 * [navigator] lives with the caller, so the open group survives the window narrowing
 * (the caller then shows it full screen) and widening again.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun GroupsListDetail(
    navigator: ThreePaneScaffoldNavigator<String>,
    onNavigateToNewGroup: () -> Unit,
    onNavigateToAddExpense: (String) -> Unit,
    onNavigateToSettleUp: (String) -> Unit,
    onNavigateToSettings: (String) -> Unit,
    onNavigateToInvite: (String) -> Unit,
    onNavigateToExpenseDetail: (groupId: String, expenseId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Back closes the open group, back to the list, then leaves. Google's default (pop
    // until the panes change) does nothing while both panes show, since closing the group
    // leaves the detail pane in place, so Back would skip straight past it. Groups opened
    // one after another close together, rather than Back stepping through each of them.
    val groupOpen = navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail
    val closeGroup = {
        scope.launch {
            while (navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail &&
                navigator.navigateBack(BackNavigationBehavior.PopLatest)
            ) Unit
        }
    }
    BackHandler(enabled = groupOpen) { closeGroup() }
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        listPane = {
            AnimatedPane {
                GroupsScreen(
                    onNavigateToNewGroup = onNavigateToNewGroup,
                    onNavigateToGroupDetail = { groupId ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, groupId) }
                    },
                    // Two panes only appear beside the rail, which has its own New group button.
                    showNewGroupButton = false,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val groupId = navigator.currentDestination?.contentKey
                if (groupId == null) {
                    NoGroupSelected()
                } else {
                    GroupDetailScreen(
                        groupId = groupId,
                        onNavigateBack = { closeGroup() },
                        onNavigateToAddExpense = { onNavigateToAddExpense(groupId) },
                        onNavigateToSettleUp = { onNavigateToSettleUp(groupId) },
                        onNavigateToSettings = { onNavigateToSettings(groupId) },
                        onNavigateToInvite = { onNavigateToInvite(groupId) },
                        onNavigateToExpenseDetail = { expenseId -> onNavigateToExpenseDetail(groupId, expenseId) },
                        // One view model per group: the pane swaps groups without leaving the screen,
                        // so an unkeyed view model would keep showing the first one.
                        viewModel = koinViewModel(key = groupDetailViewModelKey(groupId)) { parametersOf(groupId) },
                    )
                }
            }
        },
    )
}

/**
 * The key of a group's view model in the Groups screen's store. The full-screen group
 * opened by folding (and closed again by unfolding) uses the same one, so its tab and
 * loaded expenses carry over instead of loading afresh.
 */
fun groupDetailViewModelKey(groupId: String) = "group-detail-$groupId"

@Composable
private fun NoGroupSelected() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Select a group",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Its expenses and balances show here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
