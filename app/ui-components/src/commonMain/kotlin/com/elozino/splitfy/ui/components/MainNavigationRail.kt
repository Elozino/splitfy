package com.elozino.splitfy.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitfy.ui.theme.AppTheme

/**
 * The side rail that replaces [MainBottomNavigation] on wide windows (tablets,
 * unfolded foldables): same tabs in the same order, same primary action, so the two
 * read as one navigation that moves to where the thumb and eye are.
 */
@Composable
fun MainNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCenterClick: () -> Unit = {},
    centerIcon: ImageVector = Icons.Default.QrCodeScanner,
    centerContentDescription: String = "Scan",
    /** For short windows (a phone on its side): tighter spacing so every tab fits. */
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            if (!compact) Spacer(Modifier.height(8.dp))
            FloatingActionButton(
                onClick = onCenterClick,
                containerColor = AppTheme.colors.brand,
                contentColor = AppTheme.colors.onBrand,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            ) {
                Icon(centerIcon, contentDescription = centerContentDescription, modifier = Modifier.size(24.dp))
            }
        },
    ) {
        // Tabs sit together in the middle of the rail, where Material recommends them.
        Spacer(Modifier.weight(1f))
        RailTabs.forEachIndexed { index, tab ->
            if (index > 0 && !compact) Spacer(Modifier.height(12.dp))
            NavigationRailItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, fontSize = 12.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = AppTheme.colors.onBrand,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = AppTheme.colors.brand,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

private class RailTab(val label: String, val icon: ImageVector)

/** Same order as the bottom bar: Home, Groups, Contacts, Account. */
private val RailTabs = listOf(
    RailTab("Home", Icons.Default.Home),
    RailTab("Groups", Icons.Default.Group),
    RailTab("Contacts", Icons.Default.Contacts),
    RailTab("Account", Icons.Default.Person),
)
