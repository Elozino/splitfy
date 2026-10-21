package com.elozino.splitfy.screens.expenses.add

import com.elozino.splitfy.components.UserAvatar
import com.elozino.splitfy.newClientRequestId
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.elozino.splitfy.PermissionStatus
import com.elozino.splitfy.PermissionType
import com.elozino.splitfy.checkPermission
import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.rememberPermissionLauncher
import com.preat.peekaboo.image.picker.ResizeOptions
import androidx.compose.ui.layout.ContentScale
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.ui.theme.primaryLight
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.ui.camera.PeekabooCamera
import com.preat.peekaboo.ui.camera.rememberPeekabooCameraState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: AddExpenseViewModel = koinViewModel { parametersOf(groupId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Stable per screen entry: a retry after a timeout must not create a duplicate.
    val clientRequestId = remember { newClientRequestId() }

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

    // Peekaboo already JPEG-encodes and downscales gallery picks; the view model
    // compresses again so camera shots and picks end up the same size.
    val scope = rememberCoroutineScope()
    val galleryLauncher = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        resizeOptions = ResizeOptions(width = 2048, height = 2048, compressionQuality = 0.9),
        onResult = { images -> images.firstOrNull()?.let(viewModel::onReceiptImage) },
    )

    when (state.step) {
        AddExpenseStep.DETAILS -> ExpenseDetailsScreen(
            state = state,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onContinue = { viewModel.save(clientRequestId) },
            onSelectSplitType = { viewModel.navigateTo(AddExpenseStep.SPLIT_TYPE) },
            onAddImage = { viewModel.setImageSheetVisible(true) },
        )
        AddExpenseStep.SPLIT_TYPE -> SplitWithScreen(
            state = state,
            viewModel = viewModel,
            onNavigateBack = { viewModel.navigateTo(AddExpenseStep.DETAILS) },
            onDone = { viewModel.navigateTo(AddExpenseStep.DETAILS) },
        )
        AddExpenseStep.CAMERA -> ReceiptCaptureScreen(
            onNavigateBack = { viewModel.navigateTo(AddExpenseStep.DETAILS) },
            onCapture = viewModel::onReceiptImage,
            onOpenGallery = {
                viewModel.navigateTo(AddExpenseStep.DETAILS)
                galleryLauncher.launch()
            },
        )
    }
    
    if (state.isImageSheetVisible) {
        ImagePickerSheet(
            onDismiss = { viewModel.setImageSheetVisible(false) },
            onTakePhoto = {
                viewModel.setImageSheetVisible(false)
                viewModel.navigateTo(AddExpenseStep.CAMERA)
            },
            onGallery = {
                viewModel.setImageSheetVisible(false)
                galleryLauncher.launch()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailsScreen(
    state: AddExpenseState,
    viewModel: AddExpenseViewModel,
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit,
    onSelectSplitType: () -> Unit,
    onAddImage: () -> Unit,
) {
    val title = state.title
    val amount = state.amountInput
    val notes = state.notes
    val selectedSplitType = state.splitMethod.label()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

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
        snackbarHost = { SnackbarHost(snackbar) },
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
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
                ) {
                    Text(
                        text = if (state.isSaving) "Saving…" else "Save",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
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
                    onValueChange = viewModel::onTitleChange,
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
                Text("Amount (${state.currency.code})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextField(
                    value = amount,
                    onValueChange = viewModel::onAmountChange,
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
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                        val all = ExpenseCategory.entries
                        viewModel.onCategoryChange(all[(all.indexOf(state.category) + 1) % all.size])
                    },
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
                            Text(
                                text = state.category.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                    }
                }
            }

            item {
                Text("Paid by", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                        // Cycle through members; every group has at least the creator.
                        val members = state.members
                        if (members.isNotEmpty()) {
                            val next = members.indexOfFirst { it.userId == state.paidBy } + 1
                            viewModel.onPaidByChange(members[next % members.size].userId)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val payer = state.members.firstOrNull { it.userId == state.paidBy }
                        Text(
                            text = payer?.displayName ?: "Select who paid",
                            fontWeight = FontWeight.Bold
                        )
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
                    onValueChange = viewModel::onNotesChange,
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

            // Hidden entirely when the server has no storage configured.
            if (state.uploadsEnabled) {
                item {
                    Text("Receipt", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    ReceiptAttachment(
                        preview = state.receiptPreview,
                        isUploading = state.isUploadingReceipt,
                        isAttached = state.receiptUrl != null,
                        onAdd = onAddImage,
                        onRemove = viewModel::removeReceipt,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptAttachment(
    preview: ByteArray?,
    isUploading: Boolean,
    isAttached: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    if (preview == null && !isUploading) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onAdd),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF5F5F5)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Add a photo of the receipt", color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(width = 120.dp, height = 160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
    ) {
        if (preview != null) {
            AsyncImage(
                model = preview,
                contentDescription = "Receipt",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isUploading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
        if (isAttached || isUploading) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove receipt", tint = Color.White, modifier = Modifier.size(16.dp))
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
private fun ReceiptCaptureScreen(
    onNavigateBack: () -> Unit,
    onCapture: (ByteArray?) -> Unit,
    onOpenGallery: () -> Unit,
) {
    val cameraState = rememberPeekabooCameraState(onCapture = onCapture)
    // Peekaboo only re-asks after a denial, so the first request is ours to make.
    var permission by remember { mutableStateOf(checkPermission(PermissionType.CAMERA)) }
    val requestPermission = rememberPermissionLauncher(PermissionType.CAMERA) { permission = it }
    LaunchedEffect(Unit) {
        if (permission != PermissionStatus.GRANTED) requestPermission()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (permission == PermissionStatus.GRANTED) {
                PeekabooCamera(state = cameraState, modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Allow camera access to photograph receipts, or pick one from your photos.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = requestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryLight),
                    ) {
                        Text("Allow camera", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

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
                IconButton(onClick = onOpenGallery) {
                    Icon(Icons.Default.Image, contentDescription = "Choose from photos", tint = Color.White, modifier = Modifier.size(32.dp))
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
    state: AddExpenseState,
    viewModel: AddExpenseViewModel,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
) {
    // Only the methods `:shared` can actually compute are offered.
    val modes = listOf(
        SplitMethod.EQUAL,
        SplitMethod.EXACT,
        SplitMethod.PERCENTAGE,
        SplitMethod.SHARES,
    )
    val selectedMode = state.splitMethod
    val totalAmount = state.amount

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
                    onClick = onDone,
                    enabled = state.splitError == null,
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
                        onClick = { viewModel.onSplitMethodChange(mode) },
                        label = { Text(mode.label()) },
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
                items(state.members, key = { it.userId.value }) { member ->
                    SplitParticipantItem(
                        avatarUrl = member.avatarUrl,
                        avatarName = member.displayName,
                        name = if (member.userId.value == state.paidBy?.value) {
                            "${'$'}{member.displayName} (payer)"
                        } else {
                            member.displayName
                        },
                        isParticipant = state.isParticipant(member.userId),
                        // The preview is the shared calculator's own output.
                        previewAmount = state.previewShares
                            .firstOrNull { it.userId == member.userId }
                            ?.amount
                            ?.toPlainString(),
                        rawValue = state.weightOf(member.userId),
                        mode = selectedMode,
                        currencyCode = state.currency.code,
                        onToggle = { viewModel.toggleParticipant(member.userId) },
                        onValueChange = { viewModel.onWeightChange(member.userId, it) }
                    )
                }
            }

            SplitSummaryFooter(state)
        }
    }
}

@Composable
private fun SplitParticipantItem(
    avatarUrl: String?,
    avatarName: String,
    name: String,
    isParticipant: Boolean,
    previewAmount: String?,
    rawValue: Long,
    mode: SplitMethod,
    currencyCode: String,
    onToggle: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(avatarUrl, avatarName)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (isParticipant && previewAmount != null) {
                Text("$previewAmount $currencyCode", color = Color.Gray, fontSize = 12.sp)
            }
        }

        if (!isParticipant) {
            Checkbox(
                checked = false,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = primaryLight)
            )
            return@Row
        }

        when (mode) {
            SplitMethod.EQUAL -> {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = primaryLight)
                )
            }
            SplitMethod.EXACT -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$currencyCode ", fontWeight = FontWeight.Bold)
                    SplitTextField(
                        value = if (rawValue == 0L) "" else (rawValue / 100.0).toFixed(2),
                        onValueChange = onValueChange
                    )
                }
            }
            SplitMethod.PERCENTAGE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SplitTextField(
                        value = if (rawValue == 0L) "" else (rawValue / 100).toString(),
                        onValueChange = onValueChange
                    )
                    Text(" %", fontWeight = FontWeight.Bold)
                }
            }
            SplitMethod.SHARES -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SplitTextField(
                        value = if (rawValue == 0L) "" else rawValue.toString(),
                        onValueChange = onValueChange
                    )
                    Text(" shares", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

/** UI copy for a split method; the enum itself is the shared contract. */
internal fun SplitMethod.label(): String = when (this) {
    SplitMethod.EQUAL -> "Equally"
    SplitMethod.EXACT -> "Unequally"
    SplitMethod.PERCENTAGE -> "By Percentages"
    SplitMethod.SHARES -> "By Shares"
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
private fun SplitSummaryFooter(state: AddExpenseState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The error comes straight from the shared calculator, so what blocks Save
        // here is exactly what the server would have rejected.
        state.splitError?.let { message ->
            Text(message, color = Color(0xFFD32F2F), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            return@Column
        }

        when (state.splitMethod) {
            SplitMethod.EQUAL -> {
                val each = state.previewShares.firstOrNull()?.amount?.toPlainString() ?: "-"
                Text("$each /person", fontWeight = FontWeight.Bold)
                Text("(${'$'}{state.participants.size} people)", color = Color.Gray, fontSize = 12.sp)
            }
            SplitMethod.EXACT -> {
                val assigned = state.participants.sumOf { state.weightOf(it) }
                val total = state.amount?.toPlainString() ?: "-"
                Text(
                    text = "${(assigned / 100.0).toFixed(2)} of $total",
                    fontWeight = FontWeight.Bold
                )
                Text("${'$'}{(state.remaining / 100.0).toFixed(2)} left", color = Color.Gray, fontSize = 12.sp)
            }
            SplitMethod.PERCENTAGE -> {
                val assigned = state.participants.sumOf { state.weightOf(it) } / 100
                Text("$assigned% of 100%", fontWeight = FontWeight.Bold)
                Text("${'$'}{state.remaining / 100}% left", color = Color.Gray, fontSize = 12.sp)
            }
            SplitMethod.SHARES -> {
                val totalShares = state.participants.sumOf { state.weightOf(it) }
                Text("$totalShares total shares", fontWeight = FontWeight.Bold)
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

