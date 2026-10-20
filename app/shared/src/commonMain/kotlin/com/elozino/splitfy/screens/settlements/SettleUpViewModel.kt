package com.elozino.splitfy.screens.settlements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SuggestedTransfer
import com.elozino.splitfy.newClientRequestId
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.repositories.SettlementRepository
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Records payments between members, outside the app (cash, bank transfer).
 *
 * The flow mirrors how the server treats money as received: the payer records a
 * payment as pending and the payee confirms it. When the payee records it
 * themselves, it is confirmed straight away, since they are the one who would
 * confirm it anyway.
 */
class SettleUpViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val settlementRepository: SettlementRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettleUpState())
    val state: StateFlow<SettleUpState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val group = async { groupRepository.get(groupId) }
            val balances = async { groupRepository.balances(groupId) }
            val settlements = async { settlementRepository.list(groupId) }

            val groupResult = group.await()
            val balancesResult = balances.await()
            val settlementsResult = settlements.await()

            _state.update { current ->
                current.copy(
                    isLoading = false,
                    currentUserId = sessionManager.currentUserId(),
                    group = groupResult.getOrNull() ?: current.group,
                    suggestedTransfers = balancesResult.getOrNull()?.suggestedTransfers
                        ?: current.suggestedTransfers,
                    settlements = settlementsResult.getOrNull() ?: current.settlements,
                    errorMessage = listOfNotNull(
                        groupResult.errorOrNull(),
                        balancesResult.errorOrNull(),
                        settlementsResult.errorOrNull(),
                    ).firstOrNull()?.userMessage(),
                )
            }
        }
    }

    /** Opens the record-payment sheet pre-filled with the full outstanding amount. */
    fun startPayment(transfer: SuggestedTransfer) = _state.update {
        it.copy(
            draft = PaymentDraft(
                from = transfer.from,
                to = transfer.to,
                amountText = transfer.amount.toPlainString(),
                clientRequestId = newClientRequestId(),
            ),
            errorMessage = null,
        )
    }

    fun onAmountChange(value: String) = _state.update { state ->
        state.copy(draft = state.draft?.copy(amountText = value))
    }

    fun onNoteChange(value: String) = _state.update { state ->
        state.copy(draft = state.draft?.copy(note = value.take(MAX_NOTE_LENGTH)))
    }

    fun dismissPayment() = _state.update { it.copy(draft = null) }

    fun submitPayment() {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.isSubmitting) return

        val amount = Money.parse(draft.amountText, current.currency)
        if (amount == null || !amount.isPositive) {
            _state.update { it.copy(errorMessage = "Enter an amount greater than zero") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }

            val created = settlementRepository.create(
                groupId,
                CreateSettlementRequest(
                    fromUserId = draft.from,
                    toUserId = draft.to,
                    amount = amount,
                    note = draft.note.trim().ifEmpty { null },
                    clientRequestId = draft.clientRequestId,
                ),
            )

            // A payee recording money they received is also the one who confirms it.
            val result = when {
                created is DataResult.Success && draft.isReceipt(current.currentUserId) ->
                    settlementRepository.confirm(groupId, created.data.id.value)
                else -> created
            }

            when (result) {
                is DataResult.Success -> {
                    _state.update { it.copy(isSubmitting = false, draft = null) }
                    load()
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun confirm(settlement: Settlement) = act(settlement) {
        settlementRepository.confirm(groupId, settlement.id.value)
    }

    /** Declines a payment someone recorded to you, or withdraws one you recorded. */
    fun cancel(settlement: Settlement) = act(settlement) {
        settlementRepository.cancel(groupId, settlement.id.value)
    }

    private fun act(settlement: Settlement, call: suspend () -> DataResult<Settlement>) {
        val id = settlement.id.value
        if (id in _state.value.busySettlementIds) return

        viewModelScope.launch {
            _state.update { it.copy(busySettlementIds = it.busySettlementIds + id, errorMessage = null) }
            val result = call()
            _state.update {
                it.copy(
                    busySettlementIds = it.busySettlementIds - id,
                    errorMessage = result.errorOrNull()?.userMessage(),
                )
            }
            if (result is DataResult.Success) load()
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    private companion object {
        const val MAX_NOTE_LENGTH = 140
    }
}
