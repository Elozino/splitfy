package com.elozino.splitify.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitify.api.dto.AddPaymentMethodRequest
import com.elozino.splitify.api.dto.UpdateProfileRequest
import com.elozino.splitify.CardTokenizer
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.CurrencyCode
import com.elozino.splitify.domain.model.PaymentMethod
import com.elozino.splitify.repositories.AuthRepository
import com.elozino.splitify.repositories.UserRepository
import com.elozino.splitify.repositories.WalletRepository
import com.elozino.splitify.SessionManager
import com.elozino.splitify.screens.account.AccountState
import com.elozino.splitify.userMessage
import com.elozino.splitify.validation.Validators
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
            val wallet = async { walletRepository.wallet() }
            val methods = async { walletRepository.paymentMethods() }

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
     * digits. The card number never reaches the Splitfy API.
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
