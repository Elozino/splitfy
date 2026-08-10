package com.elozino.splitify.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.api.dto.Contact
import com.elozino.splitify.api.dto.CreateTransferRequest
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.LedgerEntry
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.Transfer
import com.elozino.splitify.domain.model.TransferKind
import com.elozino.splitify.domain.model.UserId
import com.elozino.splitify.repositories.ContactRepository
import com.elozino.splitify.repositories.TransferRepository
import com.elozino.splitify.repositories.WalletRepository
import com.elozino.splitify.TransactionType
import com.elozino.splitify.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionUiState(
    val isLoading: Boolean = true,
    val balance: Money = Money.zero(CurrencyCode.NGN),
    val contacts: List<Contact> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(),
    val pendingRequests: List<Transfer> = emptyList(),
    val filter: TransactionType = TransactionType.ALL,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val isSubmitting: Boolean = false,
    val lastTransfer: Transfer? = null,
    val errorMessage: String? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
}

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

    private val _state = MutableStateFlow(TransactionUiState())
    val state: StateFlow<TransactionUiState> = _state.asStateFlow()

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
