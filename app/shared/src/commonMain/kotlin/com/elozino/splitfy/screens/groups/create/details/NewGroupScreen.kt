package com.elozino.splitfy.screens.groups.create.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.GroupCategory
import com.elozino.splitfy.ui.theme.primaryLight
import com.elozino.splitfy.screens.groups.create.shared.NewGroupViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewGroupScreen(
    viewModel: NewGroupViewModel,
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val title = state.name
    val description = state.description
    val selectedCategory = state.category
    val selectedCurrency = state.currency.code
    val currencies = listOf("NGN", "USD", "EUR", "GBP")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("New Group", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top= 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, primaryLight)
                ) {
                    Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { if (viewModel.validateDetails()) onContinue() },
                    enabled = state.canContinue,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(24.dp)
        ) {
            item {
                // Group Image Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5F5))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { /* TODO: Pick Image */ },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Upload Cover Image", color = Color.Gray, fontSize = 14.sp)
                    }
                    // Edit icon overlay - normally visible when image is set
                    Box(
                       modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(32.dp).background(primaryLight, CircleShape),
                       contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Text("Title", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextField(
                    value = title,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("Title", color = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Text("Description", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextField(
                    value = description,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 100.dp),
                    placeholder = { Text("Description", color = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Text("Currency", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setCurrencyMenuVisible(true) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F5F5)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedCurrency, color = Color.Black)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    DropdownMenu(
                        expanded = state.isCurrencyMenuVisible,
                        onDismissRequest = { viewModel.setCurrencyMenuVisible(false) },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                    ) {
                        currencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text(currency) },
                                onClick = {
                                    viewModel.onCurrencyChange(CurrencyCode(currency))
                                    viewModel.setCurrencyMenuVisible(false)
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Text("Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                // Labels are UI copy; the value sent to the API is the shared enum.
                val categories = listOf(
                    GroupCategory.TRIP to "Trip ✈️",
                    GroupCategory.HOME to "Home 👨‍👩‍👧‍👦",
                    GroupCategory.COUPLE to "Couple 💑",
                    GroupCategory.EVENT to "Event 🗓️",
                    GroupCategory.PROJECT to "Project 💼",
                    GroupCategory.OTHER to "Other 🧩"
                )
                
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    categories.forEach { (category, label) ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onCategoryChange(category) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(100.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White,
                                selectedContainerColor = primaryLight,
                                labelColor = Color.Black,
                                selectedLabelColor = Color.Black
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.LightGray.copy(alpha = 0.5f),
                                selectedBorderColor = primaryLight,
                                borderWidth = 1.dp,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}


