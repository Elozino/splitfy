package com.elozino.splitify.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitify.feature.activity.model.ActivityItemData
import com.elozino.splitify.feature.activity.presentation.ActivityViewModel
import com.elozino.splitify.shared.api.dto.ActivityFilter
import com.elozino.splitify.shared.api.dto.DebtEntry
import org.koin.compose.viewmodel.koinViewModel
import com.elozino.splitify.feature.activity.model.ActivitySection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPay: () -> Unit = {},
    onNavigateToRequest: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf(
        ActivityFilter.ALL to "All",
        ActivityFilter.YOU_OWE to "You Owe",
        ActivityFilter.OWED_TO_YOU to "Owe to you",
    )
    val selectedTabIndex = tabs.indexOfFirst { it.first == state.filter }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Activity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, (filter, title) ->
                    val isSelected = selectedTabIndex == index
                    Button(
                        onClick = { viewModel.onFilterChange(filter) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFFFEBB1B) else Color.Transparent,
                            contentColor = if (isSelected) Color.Black else Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp),
                        elevation = null
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Grouped by day, newest first; every line comes from the ledger.
            val activities = state.entries
                .groupBy { it.lastActivityAt.toString().take(10) }
                .map { (day, entries) -> ActivitySection(day, entries.map { it.toItem(state.currentUserId) }) }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFEBB1B))
                }
                return@Column
            }

            if (state.isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing outstanding 🎉", color = Color.Gray, fontSize = 16.sp)
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                activities.forEach { section ->
                    item {
                        Text(
                            text = section.date,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(section.items) { item ->
                        ActivityItem(
                            data = item,
                            onActionClick = {
                                if (item.actionLabel == "Pay") onNavigateToPay()
                                else if (item.actionLabel == "Request") onNavigateToRequest()
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = Color.LightGray.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Turns a debt line into the row model, phrased from the viewer's point of view.
 */
private fun DebtEntry.toItem(currentUserId: String?): ActivityItemData {
    val youArePayer = fromUserId.value == currentUserId
    val youArePayee = toUserId.value == currentUserId
    return ActivityItemData(
        name = if (youArePayer) "$fromName (You)" else fromName,
        status = "owes",
        target = if (youArePayee) "$toName (You)" else toName,
        amount = amount.toPlainString() + " " + amount.currency.code,
        actionLabel = when {
            isSettled -> "Paid"
            youArePayer -> "Pay"
            else -> "Request"
        },
        isOwed = youArePayee,
        isCompleted = isSettled,
    )
}

@Composable
fun ActivityItem(data: ActivityItemData, onActionClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "${data.status} ${data.target}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Text(
            text = data.amount,
            color = if (data.isCompleted) Color.Gray else Color(0xFFE57373),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Button(
            onClick = onActionClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (data.isCompleted) Color.Transparent else Color(0xFFFEBB1B),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier
                .height(32.dp)
                .then(
                    if (data.isCompleted) Modifier.background(Color.Transparent).padding(0.dp) else Modifier
                ),
            border = if (data.isCompleted) androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray) else null,
            elevation = null
        ) {
            Text(text = data.actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
