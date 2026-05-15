package com.elozino.splitify.feature.split.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.ui.theme.primaryLight
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.ui.camera.PeekabooCamera
import com.preat.peekaboo.ui.camera.rememberPeekabooCameraState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String,
    onNavigateBack: () -> Unit
) {
    var step by remember { mutableStateOf(AddExpenseStep.DETAILS) }
    var splitType by remember { mutableStateOf("Equally") }
    var showImageSheet by remember { mutableStateOf(false) }

    when (step) {
        AddExpenseStep.DETAILS -> ExpenseDetailsScreen(
            onNavigateBack = onNavigateBack,
            onContinue = { /* Save logic */ onNavigateBack() },
            onSelectSplitType = { step = AddExpenseStep.SPLIT_TYPE },
            onAddImage = { showImageSheet = true },
            selectedSplitType = splitType
        )
        AddExpenseStep.SPLIT_TYPE -> SplitWithScreen(
            onNavigateBack = { step = AddExpenseStep.DETAILS },
            onSplitTypeSelected = { 
                splitType = it
                step = AddExpenseStep.DETAILS 
            }
        )
        AddExpenseStep.CAMERA -> ReceiptCaptureScreen(
            onNavigateBack = { step = AddExpenseStep.DETAILS },
            onCapture = { step = AddExpenseStep.DETAILS }
        )
    }
    
    if (showImageSheet) {
        ImagePickerSheet(
            onDismiss = { showImageSheet = false },
            onTakePhoto = {
                showImageSheet = false
                step = AddExpenseStep.CAMERA
            },
            onGallery = {
                showImageSheet = false
            }
        )
    }
}

enum class AddExpenseStep {
    DETAILS, SPLIT_TYPE, CAMERA
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailsScreen(
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit,
    onSelectSplitType: () -> Unit,
    onAddImage: () -> Unit,
    selectedSplitType: String
) {
    var title by remember { mutableStateOf("Japanese Grocery Shopping") }
    var amount by remember { mutableStateOf("200.00") }
    var notes by remember { mutableStateOf("Shop for Japanese Groceries to Cook Yourself at the Inn.") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Add New Expense", fontWeight = FontWeight.Bold) },
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
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
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
                    onClick = onContinue,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("Title", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Text("Amount (USD $)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Text("Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { /* TODO */ },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(24.dp).background(Color(0xFF4CAF50), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Groceries", fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                    }
                }
            }

            item {
                Text("Paid by", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { /* TODO */ },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Andrew Ainsley (You)", fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                    }
                }
            }

            item {
                Text("Split by", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onSelectSplitType() },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedSplitType, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                    }
                }
            }

            item {
                Text("Notes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 100.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onAddImage() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                        Text("Add an Image", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("IMG-12202023.jpg", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        IconButton(onClick = { /* Remove */ }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePickerSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onGallery: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Add an Image", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onTakePhoto() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFE91E63), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Take a Picture", fontWeight = FontWeight.Bold)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onGallery() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFF2196F3), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Select from Gallery", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptCaptureScreen(onNavigateBack: () -> Unit, onCapture: () -> Unit) {
    val cameraState = rememberPeekabooCameraState(onCapture = { onCapture() })

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PeekabooCamera(state = cameraState, modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(300.dp)
                    .height(450.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            )
            
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Gallery */ }) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(4.dp, primaryLight, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { cameraState.capture() }
                )
                
                IconButton(onClick = { cameraState.toggleCamera() }) {
                    Icon(Icons.Default.Cached, contentDescription = "Flip", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitWithScreen(
    onNavigateBack: () -> Unit,
    onSplitTypeSelected: (String) -> Unit
) {
    val modes = listOf("Equally", "Unequally", "By Percentages", "By Shares", "By Adjustment")
    var selectedMode by remember { mutableStateOf("Equally") }
    
    val totalAmount = 120.0
    val participants = remember {
        mutableStateListOf(
            ParticipantSplit("Andrew Ainsley (You)", 0.0),
            ParticipantSplit("Charlotte Hanlin", 0.0),
            ParticipantSplit("Darron Kulikowski", 0.0),
            ParticipantSplit("Kristin Watson", 0.0),
            ParticipantSplit("Joseph Thomas", 0.0),
            ParticipantSplit("Maryland Winkles", 0.0)
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Split by", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
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
                    .navigationBarsPadding()
                    .padding(24.dp),
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
                    onClick = { onSplitTypeSelected(selectedMode) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(modes) { mode ->
                    val isSelected = selectedMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedMode = mode },
                        label = { Text(mode) },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryLight,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(participants) { participant ->
                    SplitParticipantItem(
                        participant = participant,
                        mode = selectedMode,
                        totalAmount = totalAmount,
                        onValueChange = { newValue ->
                            val index = participants.indexOf(participant)
                            participants[index] = participant.copy(value = newValue)
                        }
                    )
                }
            }

            SplitSummaryFooter(selectedMode, participants, totalAmount)
        }
    }
}

@Composable
private fun SplitParticipantItem(
    participant: ParticipantSplit,
    mode: String,
    totalAmount: Double,
    onValueChange: (Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(participant.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (mode == "Equally") {
                Text("$${(totalAmount / 6).toFixed(2)}", color = Color.Gray, fontSize = 12.sp)
            }
        }

        when (mode) {
            "Equally" -> {
                Checkbox(
                    checked = true, 
                    onCheckedChange = {}, 
                    colors = CheckboxDefaults.colors(checkedColor = primaryLight)
                )
            }
            "Unequally" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$ ", fontWeight = FontWeight.Bold)
                    SplitTextField(
                        value = if (participant.value == 0.0) "" else participant.value.toString(),
                        onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) }
                    )
                }
            }
            "By Percentages" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SplitTextField(
                        value = if (participant.value == 0.0) "" else participant.value.toInt().toString(),
                        onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) }
                    )
                    Text(" %", fontWeight = FontWeight.Bold)
                }
            }
            "By Shares" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SplitTextField(
                        value = if (participant.value == 0.0) "" else participant.value.toInt().toString(),
                        onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) }
                    )
                    Text(" shares", color = Color.Gray, fontSize = 12.sp)
                }
            }
            "By Adjustment" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+ ", fontWeight = FontWeight.Bold)
                    SplitTextField(
                        value = if (participant.value == 0.0) "" else participant.value.toString(),
                        onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitTextField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.width(80.dp),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End, fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    )
}

@Composable
private fun SplitSummaryFooter(mode: String, participants: List<ParticipantSplit>, totalAmount: Double) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (mode) {
            "Equally" -> {
                Text("$${(totalAmount / participants.size).toFixed(2)} /person", fontWeight = FontWeight.Bold)
                Text("(${participants.size} people)", color = Color.Gray, fontSize = 12.sp)
            }
            "Unequally" -> {
                val currentTotal = participants.sumOf { it.value }
                val left = totalAmount - currentTotal
                Text("$${currentTotal.toFixed(2)} of $${totalAmount.toFixed(2)}", fontWeight = FontWeight.Bold)
                Text("$${left.toFixed(2)} left", color = Color.Gray, fontSize = 12.sp)
            }
            "By Percentages" -> {
                val totalPercent = participants.sumOf { it.value }.toInt()
                val leftPercent = 100 - totalPercent
                Text("$totalPercent% of 100%", fontWeight = FontWeight.Bold)
                Text("$leftPercent% left", color = Color.Gray, fontSize = 12.sp)
            }
            "By Shares" -> {
                val totalShares = participants.sumOf { it.value }.toInt()
                Text("$totalShares total shares", fontWeight = FontWeight.Bold)
            }
            "By Adjustment" -> {
                // No specific footer shown in 56
            }
        }
    }
}

private fun Double.toFixed(digits: Int): String {
    val s = this.toString()
    val dotIndex = s.indexOf('.')
    if (dotIndex == -1) return s + "." + "0".repeat(digits)
    val fraction = s.substring(dotIndex + 1)
    return if (fraction.length >= digits) {
        s.substring(0, dotIndex + digits + 1)
    } else {
        s + "0".repeat(digits - fraction.length)
    }
}

data class ParticipantSplit(val name: String, val value: Double)
