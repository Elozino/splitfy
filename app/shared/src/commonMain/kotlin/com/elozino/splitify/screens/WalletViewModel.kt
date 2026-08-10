package com.elozino.splitify.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.api.dto.AddPaymentMethodRequest
import com.elozino.splitify.api.dto.TopUpRequest
import com.elozino.splitify.api.dto.WithdrawRequest
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.PaymentMethod
import com.elozino.splitify.repositories.WalletRepository
import com.elozino.splitify.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the flow is adding money or taking it out. */
enum class WalletOperation { TOP_UP, WITHDRAW }

data class WalletUiState(
    val isLoading: Boolean = true,
    val balance: Money = Money.zero(CurrencyCode.NGN),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedMethodId: String? = null,
    /** Raw keypad text, e.g. `"1,000.00"`. Parsed through the shared `Money.parse`. */
    val amountInput: String = "",
    val isSubmitting: Boolean = false,
    val completed: Boolean = false,
    val errorMessage: String? = null,
) {
    val currency: CurrencyCode get() = balance.currency

    /** `null` while the typed amount is not yet a valid number. */
    val amount: Money? get() = Money.parse(amountInput, currency)

    val selectedMethod: PaymentMethod?
        get() = paymentMethods.firstOrNull { it.id == selectedMethodId }
            ?: paymentMethods.firstOrNull { it.isDefault }

    val canContinue: Boolean get() = amount?.isPositive == true && !isSubmitting

    val canSubmit: Boolean get() = canContinue && selectedMethod != null

    /** A withdrawal cannot exceed the balance; the server enforces this too. */
    fun exceedsBalance(operation: WalletOperation): Boolean =
        operation == WalletOperation.WITHDRAW && (amount?.let { it > balance } ?: false)
}

class WalletViewModel(
    private val walletRepository: WalletRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val wallet = async { walletRepository.wallet() }
            val methods = async { walletRepository.paymentMethods() }

            val walletResult = wallet.await()
            val methodsResult = methods.await()

            _state.update { current ->
                current.copy(
                    isLoading = false,
                    balance = (walletResult as? DataResult.Success)?.data?.balance ?: current.balance,
                    paymentMethods = (methodsResult as? DataResult.Success)?.data ?: current.paymentMethods,
                    errorMessage = listOfNotNull(
                        (walletResult as? DataResult.Failure)?.error,
                        (methodsResult as? DataResult.Failure)?.error,
                    ).firstOrNull()?.userMessage(),
                )
            }
        }
    }

    /** Keypad input: digits and a single decimal separator only. */
    fun onDigit(digit: String) = _state.update { current ->
        val cleaned = current.amountInput.replace(",", "")
        if (digit == "." && cleaned.contains('.')) return@update current
        val decimals = cleaned.substringAfter('.', "")
        if (digit != "." && cleaned.contains('.') && decimals.length >= current.currency.minorUnitDigits) {
            return@update current
        }
        current.copy(amountInput = (cleaned + digit).trimStart('0').ifEmpty { "0" }, errorMessage = null)
    }

    fun onBackspace() = _state.update {
        it.copy(amountInput = it.amountInput.replace(",", "").dropLast(1), errorMessage = null)
    }

    fun onAmountChange(value: String) = _state.update { it.copy(amountInput = value, errorMessage = null) }

    fun onMethodSelected(methodId: String) = _state.update { it.copy(selectedMethodId = methodId) }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    /** Resets after the success sheet is dismissed so the flow can be re-entered. */
    fun reset() = _state.update {
        it.copy(amountInput = "", completed = false, isSubmitting = false, errorMessage = null)
    }

    fun submit(operation: WalletOperation, clientRequestId: String) {
        val current = _state.value
        val amount = current.amount ?: return
        val method = current.selectedMethod ?: return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = when (operation) {
                WalletOperation.TOP_UP -> walletRepository.topUp(
                    TopUpRequest(amount, method.id, clientRequestId)
                )
                WalletOperation.WITHDRAW -> walletRepository.withdraw(
                    WithdrawRequest(amount, method.id, clientRequestId)
                )
            }
            when (result) {
                is DataResult.Success -> _state.update {
                    it.copy(isSubmitting = false, completed = true, balance = result.data.wallet.balance)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun addPaymentMethod(request: AddPaymentMethodRequest) {
        viewModelScope.launch {
            when (val result = walletRepository.addPaymentMethod(request)) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        paymentMethods = it.paymentMethods + result.data,
                        selectedMethodId = result.data.id,
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.userMessage())
                }
            }
        }
    }
}
