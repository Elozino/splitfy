package com.elozino.splitify.screens.account

import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.PaymentMethod
import com.elozino.splitify.domain.model.User

data class AccountState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val balance: Money = Money.zero(CurrencyCode.NGN),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val isSaving: Boolean = false,
    val savedAt: Long? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val signedOut: Boolean = false,
    val isSigningOut: Boolean = false,
) {
    val displayName: String get() = user?.displayName.orEmpty()
    val email: String get() = user?.email.orEmpty()
    val phoneNumber: String get() = user?.phoneNumber.orEmpty()
    val defaultMethod: PaymentMethod? get() = paymentMethods.firstOrNull { it.isDefault }
}
