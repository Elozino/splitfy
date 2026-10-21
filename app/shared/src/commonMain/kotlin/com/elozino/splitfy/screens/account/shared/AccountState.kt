package com.elozino.splitfy.screens.account.shared

import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.PaymentMethod
import com.elozino.splitfy.domain.model.User

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
    val isQrDialogVisible: Boolean = false,
    val isLogoutDialogVisible: Boolean = false,
    val isDeleteAccountDialogVisible: Boolean = false,
    val isDeletingAccount: Boolean = false,
    /** Why the server refused to delete the account, e.g. the groups still owing money. */
    val deleteAccountError: String? = null,
    val profileFullName: String = "",
    val profilePhone: String = "",
    val isThemeSheetVisible: Boolean = false,
    val selectedTheme: String = "Light",
    val selectedLanguage: String = "English (US)",
    val selectedFaqCategory: String = "General",
    val expandedFaqQuestion: String? = null,
    val cardHolder: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val selectedBillingTab: Int = 0,
    val paymentStep: Int = 0,
    val isPaymentProcessing: Boolean = false,
    val selectedPaymentMethodId: String? = null,
) {
    val displayName: String get() = user?.displayName.orEmpty()
    val email: String get() = user?.email.orEmpty()
    val phoneNumber: String get() = user?.phoneNumber.orEmpty()
    val defaultMethod: PaymentMethod? get() = paymentMethods.firstOrNull { it.isDefault }
}

