package com.elozino.splitfy.screens.groups.detail.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.SplitPayload
import com.elozino.splitfy.api.dto.UpdateGroupRequest
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.uploads.ImageCompressor
import com.elozino.splitfy.uploads.UploadFile
import com.elozino.splitfy.uploads.UploadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.Balance
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.ExpenseShare
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SuggestedTransfer
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.domain.split.SplitCalculator
import com.elozino.splitfy.domain.split.SplitInput
import com.elozino.splitfy.domain.split.SplitOutcome
import com.elozino.splitfy.repositories.ExpenseRepository
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupDetailViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
    private val uploadRepository: UploadRepository,
    private val imageCompressor: ImageCompressor,
    /** Where photos are decoded and re-encoded; tests pass their own dispatcher. */
    private val imageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupDetailState())
    val state: StateFlow<GroupDetailState> = _state.asStateFlow()

    val currentUserId: String? get() = sessionManager.currentUserId()

    init {
        load()
        viewModelScope.launch {
            val enabled = uploadRepository.config().getOrNull()?.enabled == true
            _state.update { it.copy(uploadsEnabled = enabled) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val group = async { groupRepository.get(groupId) }
            val expenses = async { expenseRepository.list(groupId) }
            val balances = async { groupRepository.balances(groupId) }

            val groupResult = group.await()
            val expensesResult = expenses.await()
            val balancesResult = balances.await()

            _state.update { current ->
                current.copy(
                    isLoading = false,
                    group = (groupResult as? DataResult.Success)?.data ?: current.group,
                    expenses = (expensesResult as? DataResult.Success)?.data?.items ?: current.expenses,
                    nextCursor = (expensesResult as? DataResult.Success)?.data?.nextCursor,
                    balances = (balancesResult as? DataResult.Success)?.data?.balances ?: current.balances,
                    suggestedTransfers = (balancesResult as? DataResult.Success)?.data?.suggestedTransfers
                        ?: current.suggestedTransfers,
                    errorMessage = listOfNotNull(
                        (groupResult as? DataResult.Failure)?.error,
                        (expensesResult as? DataResult.Failure)?.error,
                        (balancesResult as? DataResult.Failure)?.error,
                    ).firstOrNull()?.userMessage(),
                    currentUserId = sessionManager.currentUserId(),
                )
            }
        }
    }

    fun onTabSelected(index: Int) = _state.update {
        it.copy(selectedTab = index, isOverflowMenuVisible = false)
    }

    fun setOverflowMenuVisible(isVisible: Boolean) =
        _state.update { it.copy(isOverflowMenuVisible = isVisible) }

    /** Appends the next page; ignored while a page is already in flight. */
    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            when (val result = expenseRepository.list(groupId, cursor)) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoadingMore = false,
                        expenses = it.expenses + result.data.items,
                        nextCursor = result.data.nextCursor,
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoadingMore = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /**
     * Live preview of how an expense would divide, computed on-device with the same
     * `:shared` calculator the server uses. The amounts shown here are therefore the
     * amounts that will be stored — no "off by one kobo" surprise after saving.
     */
    fun previewSplit(total: Money, input: SplitInput): List<ExpenseShare>? =
        when (val outcome = SplitCalculator.calculate(total, input)) {
            is SplitOutcome.Success -> outcome.shares
            is SplitOutcome.Failure -> null
        }

    fun addExpense(
        description: String,
        total: Money,
        paidBy: UserId,
        split: SplitPayload,
        category: ExpenseCategory = ExpenseCategory.OTHER,
        notes: String? = null,
        clientRequestId: String? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val result = expenseRepository.create(
                groupId,
                CreateExpenseRequest(
                    description = description,
                    total = total,
                    category = category,
                    paidBy = paidBy,
                    split = split,
                    notes = notes,
                    clientRequestId = clientRequestId,
                ),
            )
            when (result) {
                is DataResult.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    // Balances are derived server-side; reload rather than guessing.
                    load()
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.delete(groupId, expenseId)) {
                is DataResult.Success -> load()
                is DataResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun setAddMemberDialogVisible(isVisible: Boolean) = _state.update {
        it.copy(isAddMemberDialogVisible = isVisible, memberEmail = "", memberError = null)
    }

    fun onMemberEmailChange(value: String) = _state.update { it.copy(memberEmail = value, memberError = null) }

    /** Adds an existing splitfy user by email. People without an account need an invite. */
    fun addMember() {
        val email = _state.value.memberEmail.trim()
        if (_state.value.isUpdatingMembers) return
        if (!email.contains('@')) {
            _state.update { it.copy(memberError = "Enter a valid email address") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isUpdatingMembers = true, memberError = null) }
            when (val result = groupRepository.addMemberByEmail(groupId, email)) {
                is DataResult.Success -> _state.update {
                    it.copy(group = result.data, isUpdatingMembers = false, isAddMemberDialogVisible = false)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(
                        isUpdatingMembers = false,
                        memberError = when (result.error) {
                            is DataError.NotFound -> "No one with that email uses splitfy yet"
                            else -> result.error.userMessage()
                        },
                    )
                }
            }
        }
    }

    fun removeMember(userId: String) {
        if (_state.value.isUpdatingMembers) return
        viewModelScope.launch {
            _state.update { it.copy(isUpdatingMembers = true) }
            when (val result = groupRepository.removeMember(groupId, userId)) {
                is DataResult.Success -> _state.update { it.copy(group = result.data, isUpdatingMembers = false) }
                // The server refuses while the member still has a balance; say why.
                is DataResult.Failure -> _state.update {
                    it.copy(isUpdatingMembers = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /** Shrinks the picked photo, uploads it, then points the group's cover at it. */
    fun onCoverImage(bytes: ByteArray?) {
        if (!_state.value.canEditCover || _state.value.isUploadingCover) return
        if (bytes == null) {
            _state.update { it.copy(errorMessage = "Couldn't read that photo. Try again.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isUploadingCover = true, errorMessage = null) }
            val compressed = withContext(imageDispatcher) { imageCompressor.compress(bytes) }
            if (compressed == null) {
                _state.update { it.copy(isUploadingCover = false, errorMessage = "That file isn't a photo we can read") }
                return@launch
            }
            val uploaded = uploadRepository.upload(groupId, UploadPurpose.GROUP_COVER, UploadFile(compressed, UploadRules.JPEG))
            val key = when (uploaded) {
                is DataResult.Success -> uploaded.data
                is DataResult.Failure -> {
                    _state.update { it.copy(isUploadingCover = false, errorMessage = uploaded.error.userMessage()) }
                    return@launch
                }
            }
            when (val result = groupRepository.update(groupId, UpdateGroupRequest(coverImageUrl = key))) {
                is DataResult.Success -> _state.update { it.copy(isUploadingCover = false, group = result.data) }
                is DataResult.Failure -> _state.update {
                    it.copy(isUploadingCover = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /** Receipts are private: each view asks the server for a link that lasts minutes. */
    fun openReceipt(expenseId: String) {
        if (_state.value.openingReceiptFor != null) return
        viewModelScope.launch {
            _state.update { it.copy(openingReceiptFor = expenseId) }
            when (val result = uploadRepository.receiptLink(groupId, expenseId)) {
                is DataResult.Success -> _state.update {
                    it.copy(openingReceiptFor = null, receiptLinkToOpen = result.data.url)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(openingReceiptFor = null, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun consumeReceiptLink() = _state.update { it.copy(receiptLinkToOpen = null) }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
}


