package com.elozino.splitfy.screens.transactions.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.Contact
import com.elozino.splitfy.api.dto.CreateTransferRequest
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.TransferKind
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.repositories.ContactRepository
import com.elozino.splitfy.repositories.TransferRepository
import com.elozino.splitfy.repositories.WalletRepository
import com.elozino.splitfy.TransactionType
import com.elozino.splitfy.Transaction
import com.elozino.splitfy.TransactionUser
import com.elozino.splitfy.PermissionStatus
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the pay/request screens and the statement.
 *
 * The statement is the wallet ledger — the same rows the backend wrote when money
 * actually moved — rather than a separate list that could drift from the balance.
 */
class TransactionViewModel(
    private val transferRepository: TransferRepository,
    private val walletRepository: WalletRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionState())
    val state: StateFlow<TransactionState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val wallet = async { walletRepository.wallet() }
            val contacts = async { contactRepository.contacts() }
            val transfers = async { transferRepository.list() }
            val ledger = async { walletRepository.ledger(types = _state.value.filter.toLedgerTypes()) }

            val walletResult = wallet.await()
            val contactsResult = contacts.await()
            val transfersResult = transfers.await()
            val ledgerResult = ledger.await()

            _state.update { current ->
                current.copy(
                    isLoading = false,
                    balance = (walletResult as? DataResult.Success)?.data?.balance ?: current.balance,
                    contacts = (contactsResult as? DataResult.Success)?.data?.contacts ?: current.contacts,
                    pendingRequests = (transfersResult as? DataResult.Success)?.data
                        ?.filter { it.status.name == "PENDING" } ?: current.pendingRequests,
                    entries = (ledgerResult as? DataResult.Success)?.data?.items ?: current.entries,
                    nextCursor = (ledgerResult as? DataResult.Success)?.data?.nextCursor,
                    errorMessage = listOfNotNull(
                        (walletResult as? DataResult.Failure)?.error,
                        (ledgerResult as? DataResult.Failure)?.error,
                    ).firstOrNull()?.userMessage(),
                )
            }
        }
    }

    fun onFilterChange(filter: TransactionType) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter, entries = emptyList(), nextCursor = null, isLoading = true) }
        viewModelScope.launch {
            when (val result = walletRepository.ledger(types = filter.toLedgerTypes())) {
                is DataResult.Success -> _state.update {
                    it.copy(isLoading = false, entries = result.data.items, nextCursor = result.data.nextCursor)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            when (val result = walletRepository.ledger(cursor, _state.value.filter.toLedgerTypes())) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isLoadingMore = false,
                        entries = it.entries + result.data.items,
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
     * A PAYMENT settles straight away; a REQUEST waits for the other person. The
     * idempotency key means a tap that times out cannot pay twice.
     */
    fun send(kind: TransferKind, counterparty: Contact, amount: Money, note: String?, clientRequestId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = transferRepository.create(
                CreateTransferRequest(
                    kind = kind,
                    counterpartyId = UserId(counterparty.userId.value),
                    amount = amount,
                    note = note,
                    clientRequestId = clientRequestId,
                )
            )
            when (result) {
                is DataResult.Success -> {
                    _state.update { it.copy(isSubmitting = false, lastTransfer = result.data) }
                    load()
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun approve(transferId: String) = respond(transferId, approve = true)

    fun decline(transferId: String) = respond(transferId, approve = false)

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    fun consumeLastTransfer() = _state.update { it.copy(lastTransfer = null) }

    fun selectUser(user: TransactionUser) = _state.update {
        it.copy(selectedUser = user, isConfirmationVisible = true)
    }

    fun dismissConfirmation() = _state.update {
        it.copy(selectedUser = null, isConfirmationVisible = false)
    }

    fun showTransferSuccess() = _state.update {
        it.copy(isConfirmationVisible = false, isSuccessVisible = true)
    }

    fun dismissTransferSuccess() = _state.update {
        it.copy(selectedUser = null, isSuccessVisible = false, lastTransfer = null)
    }

    fun selectTransaction(transaction: Transaction) = _state.update {
        it.copy(selectedTransaction = transaction)
    }

    fun dismissTransaction() = _state.update {
        it.copy(selectedTransaction = null, isReceiptVisible = false, isReceiptShareVisible = false)
    }

    fun setReceiptVisible(isVisible: Boolean) = _state.update { it.copy(isReceiptVisible = isVisible) }

    fun setReceiptShareVisible(isVisible: Boolean) =
        _state.update { it.copy(isReceiptShareVisible = isVisible) }

    fun navigateScanTo(step: ScanStep) = _state.update { it.copy(scanStep = step) }

    fun selectScanRecipient(contact: Contact) = _state.update {
        it.copy(scanRecipient = contact, scanStep = ScanStep.PROFILE, isRecipientPickerVisible = false)
    }

    fun onScanAmountChange(value: String) = _state.update { it.copy(scanAmount = value) }

    fun setRecipientPickerVisible(isVisible: Boolean) =
        _state.update { it.copy(isRecipientPickerVisible = isVisible) }

    fun onCameraPermissionChange(status: PermissionStatus) =
        _state.update { it.copy(cameraPermissionStatus = status) }

    private fun respond(transferId: String, approve: Boolean) {
        viewModelScope.launch {
            val result = if (approve) transferRepository.approve(transferId)
            else transferRepository.decline(transferId)

            when (result) {
                is DataResult.Success -> load()
                is DataResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.userMessage())
                }
            }
        }
    }
}

