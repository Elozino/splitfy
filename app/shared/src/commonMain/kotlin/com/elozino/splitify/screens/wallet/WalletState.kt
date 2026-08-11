package com.elozino.splitify.screens

import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.PaymentMethod

enum class WalletOperation { TOP_UP, WITHDRAW }

data class WalletState(
    val isLoading: Boolean = true,
    val balance: Money = Money.zero(CurrencyCode.NGN),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedMethodId: String? = null,
    val amountInput: String = "",
    val isSubmitting: Boolean = false,
    val completed: Boolean = false,
    val errorMessage: String? = null,
) {
    val currency: CurrencyCode get() = balance.currency
    val amount: Money? get() = Money.parse(amountInput, currency)
    val selectedMethod: PaymentMethod?
        get() = paymentMethods.firstOrNull { it.id == selectedMethodId }
            ?: paymentMethods.firstOrNull { it.isDefault }
    val canContinue: Boolean get() = amount?.isPositive == true && !isSubmitting
    val canSubmit: Boolean get() = canContinue && selectedMethod != null
    fun exceedsBalance(operation: WalletOperation): Boolean =
        operation == WalletOperation.WITHDRAW && (amount?.let { it > balance } ?: false)
}
