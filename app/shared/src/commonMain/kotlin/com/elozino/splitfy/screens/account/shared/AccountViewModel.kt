package com.elozino.splitfy.screens.account.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.api.dto.AddPaymentMethodRequest
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.CardTokenizer
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.FeatureFlags
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.PaymentMethod
import com.elozino.splitfy.repositories.AuthRepository
import com.elozino.splitfy.repositories.UserRepository
import com.elozino.splitfy.repositories.WalletRepository
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.userMessage
import com.elozino.splitfy.validation.Validators
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.elozino.splitfy.screens.expenses.add.label

/**
 * Backs the account tab: profile, the settings that actually persist, payment
 * methods, and sign-out.
 */
class AccountViewModel(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val cardTokenizer: CardTokenizer,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val profile = async { userRepository.me() }
            val wallet = async { if (FeatureFlags.WALLET) walletRepository.wallet() else null }
            val methods = async { if (FeatureFlags.WALLET) walletRepository.paymentMethods() else null }

            val profileResult = profile.await()
            val walletResult = wallet.await()
            val methodsResult = methods.await()

            (profileResult as? DataResult.Success)?.data?.let(sessionManager::onUserLoaded)

            _state.update { current ->
                current.copy(
                    isLoading = false,
                    user = (profileResult as? DataResult.Success)?.data ?: current.user,
                    balance = (walletResult as? DataResult.Success)?.data?.balance ?: current.balance,
                    paymentMethods = (methodsResult as? DataResult.Success)?.data ?: current.paymentMethods,
                    errorMessage = (profileResult as? DataResult.Failure)?.error?.userMessage(),
                    profileFullName = (profileResult as? DataResult.Success)?.data?.displayName
                        ?: current.profileFullName,
                    profilePhone = (profileResult as? DataResult.Success)?.data?.phoneNumber
                        ?: current.profilePhone,
                    selectedPaymentMethodId = current.selectedPaymentMethodId
                        ?: (methodsResult as? DataResult.Success)?.data?.firstOrNull { it.isDefault }?.id
                        ?: (methodsResult as? DataResult.Success)?.data?.firstOrNull()?.id,
                )
            }
        }
    }

    fun updateProfile(
        displayName: String? = null,
        phoneNumber: String? = null,
        avatarUrl: String? = null,
        defaultCurrency: CurrencyCode? = null,
    ) {
        // Same rules the server applies, so the user sees the error before the trip.
        val errors = buildList {
            displayName?.let { name -> Validators.displayName(name)?.let(::add) }
            phoneNumber?.let { phone -> Validators.phoneNumber(phone)?.let(::add) }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors.associate { e -> e.field to e.message }) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, fieldErrors = emptyMap(), errorMessage = null) }
            val result = userRepository.updateProfile(
                UpdateProfileRequest(displayName, phoneNumber, avatarUrl, defaultCurrency)
            )
            when (result) {
                is DataResult.Success -> {
                    sessionManager.onUserLoaded(result.data)
                    _state.update {
                        it.copy(isSaving = false, user = result.data, savedAt = it.savedAt.next())
                    }
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /**
     * Tokenises the card on-device, then registers only the token and the display
     * digits. The card number never reaches the splitfy API.
     */
    fun addCard(cardNumber: String, expiry: String, cvv: String, cardHolder: String) {
        val month = expiry.substringBefore('/').trim().toIntOrNull() ?: 0
        val year = expiry.substringAfter('/', "").trim().toIntOrNull() ?: 0

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val tokenized = cardTokenizer.tokenize(cardNumber, month, year, cvv, cardHolder)

            tokenized.fold(
                onSuccess = { card ->
                    val result = walletRepository.addPaymentMethod(
                        AddPaymentMethodRequest(
                            type = card.type,
                            label = card.brand.replaceFirstChar { it.uppercase() },
                            providerToken = card.providerToken,
                            brand = card.brand,
                            last4 = card.last4,
                        )
                    )
                    when (result) {
                        is DataResult.Success -> _state.update {
                            it.copy(
                                isSaving = false,
                                paymentMethods = it.paymentMethods + result.data,
                                savedAt = (it.savedAt ?: 0L) + 1,
                            )
                        }
                        is DataResult.Failure -> _state.update {
                            it.copy(isSaving = false, errorMessage = result.error.userMessage())
                        }
                    }
                },
                onFailure = { failure ->
                    _state.update {
                        it.copy(isSaving = false, errorMessage = failure.message ?: "That card was rejected")
                    }
                },
            )
        }
    }

    fun addPaymentMethod(request: AddPaymentMethodRequest) {
        viewModelScope.launch {
            when (val result = walletRepository.addPaymentMethod(request)) {
                is DataResult.Success -> _state.update {
                    it.copy(paymentMethods = it.paymentMethods + result.data)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun makeDefault(methodId: String) = mutateMethods { walletRepository.makeDefaultPaymentMethod(methodId) }

    fun deletePaymentMethod(methodId: String) = mutateMethods { walletRepository.deletePaymentMethod(methodId) }

    fun setQrDialogVisible(isVisible: Boolean) = _state.update { it.copy(isQrDialogVisible = isVisible) }

    fun setLogoutDialogVisible(isVisible: Boolean) = _state.update { it.copy(isLogoutDialogVisible = isVisible) }

    fun onProfileFullNameChange(value: String) = _state.update { it.copy(profileFullName = value) }

    fun onProfilePhoneChange(value: String) = _state.update { it.copy(profilePhone = value) }

    fun setThemeSheetVisible(isVisible: Boolean) = _state.update { it.copy(isThemeSheetVisible = isVisible) }

    fun selectTheme(theme: String) = _state.update { it.copy(selectedTheme = theme) }

    fun selectLanguage(language: String) = _state.update { it.copy(selectedLanguage = language) }

    fun selectFaqCategory(category: String) = _state.update { it.copy(selectedFaqCategory = category) }

    fun toggleFaq(question: String) = _state.update { current ->
        current.copy(expandedFaqQuestion = question.takeUnless { it == current.expandedFaqQuestion })
    }

    fun onCardHolderChange(value: String) = _state.update { it.copy(cardHolder = value) }
    fun onCardNumberChange(value: String) = _state.update { it.copy(cardNumber = value) }
    fun onCardExpiryChange(value: String) = _state.update { it.copy(cardExpiry = value) }
    fun onCardCvvChange(value: String) = _state.update { it.copy(cardCvv = value) }

    fun selectBillingTab(index: Int) = _state.update { it.copy(selectedBillingTab = index) }

    fun setPaymentStep(step: Int) = _state.update { it.copy(paymentStep = step) }

    fun selectPaymentMethod(methodId: String) = _state.update { it.copy(selectedPaymentMethodId = methodId) }

    fun setPaymentProcessing(isProcessing: Boolean) =
        _state.update { it.copy(isPaymentProcessing = isProcessing) }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(isSigningOut = true) }
            authRepository.logout()
            _state.update { it.copy(isSigningOut = false, signedOut = true) }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    private fun mutateMethods(action: suspend () -> DataResult<List<PaymentMethod>>) {
        viewModelScope.launch {
            when (val result = action()) {
                is DataResult.Success -> _state.update { it.copy(paymentMethods = result.data) }
                is DataResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /** A monotonically increasing marker so the UI can show "Saved" once per save. */
    private fun Long?.next(): Long = (this ?: 0L) + 1
}

