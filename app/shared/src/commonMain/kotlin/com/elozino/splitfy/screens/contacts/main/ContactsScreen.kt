package com.elozino.splitfy.screens.contacts.main

import com.elozino.splitfy.components.UserAvatar
import com.elozino.splitfy.ui.components.RefreshableBox
import com.elozino.splitfy.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme
import com.elozino.splitfy.ui.components.SkeletonListItem
import com.elozino.splitfy.ui.components.SkeletonList
import com.elozino.splitfy.FeatureFlags
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.api.dto.Contact
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.elozino.splitfy.adaptive.LocalWindowLayout
import com.elozino.splitfy.adaptive.ReadableWidth
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ContactScreen(
    onVisibilityChanged: (Boolean) -> Unit,
    viewModel: ContactsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val twoPanes = LocalWindowLayout.current.useTwoPanes
    val layout = contactsLayoutFor(state.currentStep, twoPanes)

    LaunchedEffect(layout) {
        onVisibilityChanged(layout.showsBottomBar())
    }
    LaunchedEffect(twoPanes) {
        if (!twoPanes) viewModel.onSinglePane()
    }
    // Back closes a contact shown on its own, as its arrow does, rather than leaving Contacts.
    BackHandler(enabled = layout == ContactsLayout.Single(ContactStep.DETAIL), onBack = viewModel::closeContact)

    when (layout) {
        is ContactsLayout.Single -> {
            // Beside the rail the caller leaves the width to Contacts, for the two panes;
            // a single screen there still reads best as a column.
            if (twoPanes) ReadableWidth { ContactStepScreen(state, layout.step, viewModel) }
            else ContactStepScreen(state, layout.step, viewModel)
        }
        is ContactsLayout.ListDetail -> ContactsListDetail(state, layout.listStep, viewModel)
    }
}

/**
 * Contacts as Google's list-detail canonical layout, like Groups: the list (or its
 * search) beside the open contact. Which contact is open lives in [ContactsViewModel],
 * so it survives the window narrowing (the contact then takes the screen) and widening.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun ContactsListDetail(
    state: ContactsState,
    listStep: ContactStep,
    viewModel: ContactsViewModel,
) {
    val selected = state.selectedContact
    BackHandler(enabled = selected != null, onBack = viewModel::closeContact)

    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    ListDetailPaneScaffold(
        directive = directive,
        value = calculateThreePaneScaffoldValue(
            maxHorizontalPartitions = directive.maxHorizontalPartitions,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            currentDestination = ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, selected?.id),
        ),
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        listPane = {
            AnimatedPane {
                ContactStepScreen(state, listStep, viewModel, onContactClick = viewModel::showContact)
            }
        },
        detailPane = {
            AnimatedPane {
                if (selected == null) {
                    NoContactSelected()
                } else {
                    ContactStepScreen(state, ContactStep.DETAIL, viewModel)
                }
            }
        },
    )
}

/** One step of Contacts, full screen or in a pane. [onContactClick] opens a row. */
@Composable
private fun ContactStepScreen(
    state: ContactsState,
    step: ContactStep,
    viewModel: ContactsViewModel,
    onContactClick: (ContactData) -> Unit = viewModel::selectContact,
) {
    when (step) {
        ContactStep.LIST -> ContactListScreen(
            contacts = state.contacts.map { it.toData() },
            favorites = state.favorites.map { it.toData() },
            isLoading = state.isLoading,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            errorMessage = state.errorMessage,
            selectedTab = state.selectedTab,
            onTabSelected = viewModel::onTabSelected,
            onToggleFavorite = { data ->
                state.contacts.firstOrNull { it.userId.value == data.id }
                    ?.let(viewModel::toggleFavorite)
            },
            onSearchClick = { viewModel.navigateTo(ContactStep.SEARCH) },
            onAddContactClick = { viewModel.navigateTo(ContactStep.ADD_CONTACT) },
            onContactClick = onContactClick,
            selectedContactId = state.selectedContact?.id,
        )
        ContactStep.SEARCH -> SearchContactScreen(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            results = state.contacts.map { it.toData() },
            directoryResults = state.directoryResults.map { user ->
                ContactData(user.id.value, user.displayName, user.email)
            },
            onNavigateBack = { viewModel.navigateTo(ContactStep.LIST) },
            onContactClick = onContactClick,
            selectedContactId = state.selectedContact?.id,
        )
        ContactStep.DETAIL -> {
            // The contact can close underneath (a delete), so read it once rather than with !!.
            val contact = state.selectedContact ?: return
            ContactDetailScreen(
                contact = contact,
                isDeleteDialogVisible = state.isDeleteDialogVisible,
                isDeleteSuccessVisible = state.isDeleteSuccessVisible,
                onDeleteClick = { viewModel.setDeleteDialogVisible(true) },
                onDeleteDismiss = { viewModel.setDeleteDialogVisible(false) },
                onDeleteConfirm = viewModel::confirmDelete,
                onToggleFavorite = {
                    state.contacts.firstOrNull { it.userId.value == contact.id }
                        ?.let(viewModel::toggleFavorite)
                },
                onNavigateBack = viewModel::closeContact,
                onDeleteSuccess = viewModel::clearSelectedContact,
            )
        }
        ContactStep.ADD_CONTACT -> AddContactScreen(
            name = state.newContactName,
            email = state.newContactEmail,
            onNameChange = viewModel::onNewContactNameChange,
            onEmailChange = viewModel::onNewContactEmailChange,
            onNavigateBack = { viewModel.navigateTo(ContactStep.LIST) },
            onContinue = { viewModel.navigateTo(ContactStep.SEARCH_RESULT) },
        )
        ContactStep.SEARCH_RESULT -> SearchResultScreen(
            isSuccessVisible = state.isAddSuccessVisible,
            onShowSuccess = { viewModel.setAddSuccessVisible(true) },
            onNavigateBack = { viewModel.navigateTo(ContactStep.ADD_CONTACT) },
            onAddSuccess = {
                viewModel.setAddSuccessVisible(false)
                viewModel.navigateTo(ContactStep.LIST)
            },
        )
    }
}

/** The detail pane before a contact is picked. */
@Composable
private fun NoContactSelected() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Select a contact",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Their details show here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Maps an API contact onto the row model, including the running balance. */
private fun Contact.toData() = ContactData(
    id = userId.value,
    name = displayName,
    email = email,
    avatarUrl = avatarUrl,
    isFavorite = isFavorite,
    balanceLabel = netBalance?.takeIf { !it.isZero }?.let { balance ->
        if (balance.isPositive) {
            "owes you ${balance.toPlainString()}"
        } else {
            "you owe ${balance.abs().toPlainString()}"
        }
    },
    sharedGroups = sharedGroupCount,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactListScreen(
    contacts: List<ContactData>,
    favorites: List<ContactData>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    errorMessage: String?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onToggleFavorite: (ContactData) -> Unit,
    onSearchClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onContactClick: (ContactData) -> Unit,
    /** The contact open beside the list, highlighted in it. */
    selectedContactId: String? = null,
) {
    val tabs = listOf("All Contacts", "Favorites")
    val visible = if (selectedTab == 1) favorites else contacts

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contacts", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddContactClick,
                containerColor = AppTheme.colors.brand,
                contentColor = AppTheme.colors.onBrand,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        RefreshableBox(isRefreshing = isRefreshing, onRefresh = onRefresh, indicatorPadding = padding) {
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(LayoutDirection.Ltr),
                    end = padding.calculateEndPadding(LayoutDirection.Ltr)
                )
            ) {
                // Search Bar Placeholder (Screen 74)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.field)
                        .clickable { onSearchClick() }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Search contact", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Tabs
                Surface(
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = AppTheme.colors.field
                ) {
                    Row {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AppTheme.colors.brand else Color.Transparent)
                                    .clickable { onTabSelected(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AppTheme.colors.onBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        errorMessage?.let { message ->
                            item {
                                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                            }
                        }

                        // Placeholders only before anything is on screen; a refresh of a
                        // visible list updates in place rather than flashing rows.
                        if (isLoading && visible.isEmpty()) {
                            item {
                                SkeletonList(count = 6, contentPadding = PaddingValues(vertical = 8.dp)) {
                                    SkeletonListItem(avatarSize = 48.dp, trailing = false)
                                }
                            }
                        }

                        if (!isLoading && visible.isEmpty()) {
                            item {
                                Text(
                                    text = "No contacts yet. People you share a group with appear here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        items(visible, key = { it.id }) { contact ->
                            ContactListItem(
                                contact = contact,
                                onClick = { onContactClick(contact) },
                                onToggleFavorite = { onToggleFavorite(contact) },
                                isSelected = contact.id == selectedContactId,
                            )
                        }
                    }
                
                    // Alphabet index (Screen 75)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp, top = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toList().forEach { char ->
                            Text(
                                text = char.toString(),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactListItem(
    contact: ContactData,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    isSelected: Boolean = false,
) {
    val highlight = AppTheme.colors.brand.copy(alpha = 0.2f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Drawn a little outside the row, so selecting one doesn't shift the list.
            .then(
                if (isSelected) Modifier.drawBehind {
                    val inset = 8.dp.toPx()
                    drawRoundRect(
                        color = highlight,
                        topLeft = Offset(-inset, -inset),
                        size = Size(size.width + 2 * inset, size.height + 2 * inset),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                } else Modifier
            )
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(contact.avatarUrl, contact.name, size = 48.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                text = contact.balanceLabel ?: contact.email,
                fontSize = 12.sp,
                color = if (contact.balanceLabel != null) AppTheme.colors.positive else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (contact.isFavorite) "Unfavourite" else "Favourite",
            tint = if (contact.isFavorite) AppTheme.colors.brand else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(20.dp).clickable { onToggleFavorite() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContactScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<ContactData>,
    directoryResults: List<ContactData>,
    onNavigateBack: () -> Unit,
    onContactClick: (ContactData) -> Unit,
    selectedContactId: String? = null,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.brand.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Search contact", color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = "Your contacts",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(results, key = { it.id }) { contact ->
                        ContactListItem(contact, onClick = { onContactClick(contact) }, isSelected = contact.id == selectedContactId)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (results.isNotEmpty()) {
                        item {
                            Text(
                                text = "Your contacts",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(results, key = { it.id }) { contact ->
                            ContactListItem(contact, onClick = { onContactClick(contact) }, isSelected = contact.id == selectedContactId)
                        }
                    }

                    // Directory results are people not yet sharing a group; the server
                    // only answers once the query is long enough to not be enumerable.
                    if (directoryResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "On Splitfy",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(directoryResults, key = { "directory-" + it.id }) { contact ->
                            ContactListItem(contact, onClick = { onContactClick(contact) }, isSelected = contact.id == selectedContactId)
                        }
                    }

                    if (results.isEmpty() && directoryResults.isEmpty()) {
                        item {
                            Text(
                                text = "Nobody matches that yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailScreen(
    contact: ContactData,
    isDeleteDialogVisible: Boolean,
    isDeleteSuccessVisible: Boolean,
    onDeleteClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onNavigateBack: () -> Unit,
    onDeleteSuccess: () -> Unit,
    onToggleFavorite: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contact", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(Icons.Default.StarBorder, contentDescription = "Favorite")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            // Pay and Request move wallet money, so they only exist with the wallet on.
            if (FeatureFlags.WALLET) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Request */ },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.brand)
                ) {
                    Text("Request", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { /* Pay */ },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
                ) {
                    Text("Pay", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            UserAvatar(contact.avatarUrl, contact.name, size = 100.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(contact.email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text("Splitfy Account", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            TextButton(onClick = onDeleteClick) {
                Text("Delete Contact", color = AppTheme.colors.negative, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (isDeleteDialogVisible) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete Contact", fontWeight = FontWeight.Bold, color = AppTheme.colors.negative)
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            text = { 
                Text(
                    "Delete \"${contact.name}\" from your contacts?", 
                    textAlign = TextAlign.Center, 
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) 
            },
            confirmButton = {
                Button(
                    onClick = onDeleteConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
                ) {
                    Text("Yes, Delete", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDeleteDismiss,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.brand)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (isDeleteSuccessVisible) {
        ModalBottomSheet(
            onDismissRequest = onDeleteSuccess,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(80.dp).background(AppTheme.colors.brand, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp), tint = AppTheme.colors.onBrand)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Contact Deleted!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContactScreen(
    name: String,
    email: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Add New Contact", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.brand)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
                ) {
                    Text("Add Contact", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Select Mobile / Name", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Brooklyn Simmons", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = AppTheme.colors.field,
                    focusedContainerColor = AppTheme.colors.field,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Email", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("name@example.com", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = AppTheme.colors.field,
                    focusedContainerColor = AppTheme.colors.field,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultScreen(
    isSuccessVisible: Boolean,
    onShowSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    onAddSuccess: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Search Contact", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.brand)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onShowSuccess,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand)
                ) {
                    Text("Add to Contact", color = AppTheme.colors.onBrand, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Brooklyn Simmons", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("Ask them for the email they signed up with", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text("Splitfy Account", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("This contact is ready to split bills with you!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }

    if (isSuccessVisible) {
        ModalBottomSheet(
            onDismissRequest = onAddSuccess,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(80.dp).background(AppTheme.colors.brand, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp), tint = AppTheme.colors.onBrand)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Contact Added Successfully!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

