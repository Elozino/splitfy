package com.elozino.splitfy.screens.expenses.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.SplitPayload
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.uploads.ImageCompressor
import com.elozino.splitfy.uploads.UploadFile
import com.elozino.splitfy.uploads.UploadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.ExpenseShare
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupMember
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.domain.split.SplitCalculator
import com.elozino.splitfy.domain.split.SplitError
import com.elozino.splitfy.domain.split.SplitInput
import com.elozino.splitfy.domain.split.SplitOutcome
import com.elozino.splitfy.repositories.ExpenseRepository
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drafts a new expense.
 *
 * The split preview runs `:shared`'s [SplitCalculator] on-device, so the amounts
 * shown before saving are exactly the amounts the server stores — the client sends
 * inputs, never the computed shares.
 */
class AddExpenseViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
    private val uploadRepository: UploadRepository,
    private val imageCompressor: ImageCompressor,
    /** Where photos are decoded and re-encoded; tests pass their own dispatcher. */
    private val imageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(AddExpenseState())
    val state: StateFlow<AddExpenseState> = _state.asStateFlow()

    private var receiptUpload: Job? = null

    init {
        load()
        viewModelScope.launch {
            val enabled = uploadRepository.config().getOrNull()?.enabled == true
            _state.update { it.copy(uploadsEnabled = enabled) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = groupRepository.get(groupId)) {
                is DataResult.Success -> _state.update { current ->
                    val me = sessionManager.currentUserId()
                    current.copy(
                        isLoading = false,
                        group = result.data,
                        // Default to an equal split between everyone, paid by the viewer.
                        participantIds = result.data.members.map { it.userId.value }.toSet(),
                        paidBy = me?.let(::UserId) ?: result.data.members.firstOrNull()?.userId,
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value, errorMessage = null) }

    fun onAmountChange(value: String) = _state.update { it.copy(amountInput = value, errorMessage = null) }

    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    fun onCategoryChange(category: ExpenseCategory) = _state.update { it.copy(category = category) }

    fun onPaidByChange(userId: UserId) = _state.update { it.copy(paidBy = userId) }

    fun onSplitMethodChange(method: SplitMethod) = _state.update { current ->
        // Switching method invalidates the old inputs; start the new one clean.
        current.copy(
            splitMethod = method,
            weights = when (method) {
                SplitMethod.SHARES -> current.participantIds.associateWith { 1L }
                else -> emptyMap()
            },
        )
    }

    fun toggleParticipant(userId: UserId) = _state.update { current ->
        val id = userId.value
        val ids = if (id in current.participantIds) current.participantIds - id
        else current.participantIds + id
        current.copy(
            participantIds = ids,
            weights = current.weights.filterKeys { it in ids },
        )
    }

    fun onWeightChange(userId: UserId, raw: String) = _state.update { current ->
        val parsed = when (current.splitMethod) {
            // Exact amounts are typed in major units; percentages in whole percent.
            SplitMethod.EXACT -> Money.parse(raw, current.currency)?.amount
            SplitMethod.PERCENTAGE -> raw.toDoubleOrNull()?.let { (it * 100).toLong() }
            else -> raw.toLongOrNull()
        } ?: 0L
        current.copy(weights = current.weights + (userId.value to parsed))
    }

    /**
     * A photo from the camera or the gallery: shrink it, then upload it straight to
     * storage. The expense only references the returned key, and only once saved.
     */
    fun onReceiptImage(bytes: ByteArray?) {
        _state.update { it.copy(step = AddExpenseStep.DETAILS) }
        if (bytes == null) {
            _state.update { it.copy(errorMessage = "Couldn't read that photo. Try again.") }
            return
        }
        receiptUpload?.cancel()
        receiptUpload = viewModelScope.launch {
            _state.update {
                it.copy(isUploadingReceipt = true, receiptUrl = null, receiptPreview = null, errorMessage = null)
            }
            val compressed = withContext(imageDispatcher) { imageCompressor.compress(bytes) }
            if (compressed == null) {
                _state.update { it.copy(isUploadingReceipt = false, errorMessage = "That file isn't a photo we can read") }
                return@launch
            }
            _state.update { it.copy(receiptPreview = compressed) }

            when (val result = uploadRepository.upload(groupId, UploadPurpose.RECEIPT, UploadFile(compressed, UploadRules.JPEG))) {
                is DataResult.Success -> _state.update { it.copy(isUploadingReceipt = false, receiptUrl = result.data) }
                is DataResult.Failure -> _state.update {
                    it.copy(
                        isUploadingReceipt = false,
                        receiptPreview = null,
                        errorMessage = result.error.userMessage(),
                    )
                }
            }
        }
    }

    /** Drops the attachment before saving. The orphaned object is harmless and unreferenced. */
    fun removeReceipt() {
        receiptUpload?.cancel()
        _state.update { it.copy(receiptUrl = null, receiptPreview = null, isUploadingReceipt = false) }
    }

    fun navigateTo(step: AddExpenseStep) = _state.update { it.copy(step = step) }

    fun setImageSheetVisible(isVisible: Boolean) =
        _state.update { it.copy(isImageSheetVisible = isVisible) }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    fun save(clientRequestId: String) {
        val current = _state.value
        val total = current.amount ?: return
        val payer = current.paidBy ?: return
        // Saving mid-upload would silently drop the receipt.
        if (current.isUploadingReceipt) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val result = expenseRepository.create(
                groupId,
                CreateExpenseRequest(
                    description = current.title.trim(),
                    total = total,
                    category = current.category,
                    paidBy = payer,
                    split = SplitPayload.from(current.toSplitInput()),
                    receiptUrl = current.receiptUrl,
                    notes = current.notes.takeIf { it.isNotBlank() },
                    clientRequestId = clientRequestId,
                ),
            )
            when (result) {
                is DataResult.Success -> _state.update { it.copy(isSaving = false, saved = true) }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }
}

