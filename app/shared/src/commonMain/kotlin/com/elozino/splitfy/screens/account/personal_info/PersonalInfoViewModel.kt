package com.elozino.splitfy.screens.account.personal_info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.repositories.AccountRepository
import com.elozino.splitfy.repositories.UserRepository
import com.elozino.splitfy.uploads.ImageCompressor
import com.elozino.splitfy.uploads.UploadFile
import com.elozino.splitfy.uploads.UploadRepository
import com.elozino.splitfy.userMessage
import com.elozino.splitfy.validation.Validators
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EmailChangeStep { CLOSED, ENTER_EMAIL, ENTER_CODE }

data class PersonalInfoState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val fullName: String = "",
    val phone: String = "",
    val currency: CurrencyCode = CurrencyCode.NGN,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    /** Bumped after each successful save, so the screen can leave once per save. */
    val savedAt: Long? = null,
    /** Whether this server stores files; the photo can't be changed without it. */
    val uploadsEnabled: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val emailChangeStep: EmailChangeStep = EmailChangeStep.CLOSED,
    val newEmail: String = "",
    val code: String = "",
    /** The address the code was sent to. */
    val pendingEmail: String? = null,
    val emailChangeError: String? = null,
    val isEmailChangeBusy: Boolean = false,
    /** Shown once after the address changes. */
    val emailChangedTo: String? = null,
) {
    val avatarUrl: String? get() = user?.avatarUrl
    val email: String get() = user?.email.orEmpty()

    companion object {
        /** The currencies the group screens offer; the default must be one of them. */
        val currencyChoices = listOf(CurrencyCode.NGN, CurrencyCode.USD, CurrencyCode.EUR, CurrencyCode.GBP)
    }
}

/**
 * Personal Info: name, phone, default currency, profile photo, and changing the
 * account's email (code sent to the new address, then confirmed).
 */
class PersonalInfoViewModel(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val uploadRepository: UploadRepository,
    private val avatarCropper: ImageCompressor,
    private val sessionManager: SessionManager,
    private val imageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(PersonalInfoState())
    val state: StateFlow<PersonalInfoState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val config = async { uploadRepository.config() }
            when (val profile = userRepository.me()) {
                is DataResult.Success -> {
                    sessionManager.onUserLoaded(profile.data)
                    _state.update { it.withUser(profile.data).copy(isLoading = false) }
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = profile.error.userMessage())
                }
            }
            val enabled = (config.await() as? DataResult.Success)?.data?.enabled == true
            _state.update { it.copy(uploadsEnabled = enabled) }
        }
    }

    fun onFullNameChange(value: String) =
        _state.update { it.copy(fullName = value, fieldErrors = it.fieldErrors - "displayName") }

    fun onPhoneChange(value: String) =
        _state.update { it.copy(phone = value, fieldErrors = it.fieldErrors - "phoneNumber") }

    fun onCurrencyChange(currency: CurrencyCode) = _state.update { it.copy(currency = currency) }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        // Same rules the server applies, so the user sees the error before the trip.
        val errors = listOfNotNull(Validators.displayName(current.fullName), Validators.phoneNumber(current.phone))
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors.associate { e -> e.field to e.message }) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, fieldErrors = emptyMap(), errorMessage = null) }
            val result = userRepository.updateProfile(
                UpdateProfileRequest(
                    displayName = current.fullName,
                    phoneNumber = current.phone,
                    defaultCurrency = current.currency,
                )
            )
            when (result) {
                is DataResult.Success -> {
                    sessionManager.onUserLoaded(result.data)
                    _state.update {
                        it.copy(isSaving = false, user = result.data, savedAt = (it.savedAt ?: 0L) + 1)
                    }
                }
                is DataResult.Failure -> _state.update { it.copy(isSaving = false).withError(result.error) }
            }
        }
    }

    /** Crops the picked photo to a square, uploads it, then points the profile at it. */
    fun onAvatarPicked(bytes: ByteArray?) {
        if (_state.value.isUploadingAvatar || !_state.value.uploadsEnabled) return
        if (bytes == null) {
            _state.update { it.copy(errorMessage = "Couldn't read that photo. Try again.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
            val cropped = withContext(imageDispatcher) { avatarCropper.compress(bytes) }
            if (cropped == null) {
                _state.update { it.copy(isUploadingAvatar = false, errorMessage = "That file isn't a photo we can read") }
                return@launch
            }
            val key = when (val uploaded = uploadRepository.uploadAvatar(UploadFile(cropped, UploadRules.JPEG))) {
                is DataResult.Success -> uploaded.data
                is DataResult.Failure -> {
                    _state.update { it.copy(isUploadingAvatar = false, errorMessage = uploaded.error.userMessage()) }
                    return@launch
                }
            }
            setAvatar(key)
        }
    }

    fun removeAvatar() {
        if (_state.value.isUploadingAvatar || _state.value.avatarUrl == null) return
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
            setAvatar("")
        }
    }

    /** Only the photo changes; unsaved edits to the other fields stay as they are. */
    private suspend fun setAvatar(key: String) {
        when (val result = userRepository.updateProfile(UpdateProfileRequest(avatarUrl = key))) {
            is DataResult.Success -> {
                sessionManager.onUserLoaded(result.data)
                _state.update { it.copy(isUploadingAvatar = false, user = result.data) }
            }
            is DataResult.Failure -> _state.update {
                it.copy(isUploadingAvatar = false, errorMessage = result.error.userMessage())
            }
        }
    }

    // --- Email change ------------------------------------------------------

    fun startEmailChange() = _state.update {
        it.copy(
            emailChangeStep = EmailChangeStep.ENTER_EMAIL,
            newEmail = "",
            code = "",
            pendingEmail = null,
            emailChangeError = null,
        )
    }

    fun dismissEmailChange() = _state.update {
        it.copy(emailChangeStep = EmailChangeStep.CLOSED, emailChangeError = null, isEmailChangeBusy = false)
    }

    fun consumeEmailChanged() = _state.update { it.copy(emailChangedTo = null) }

    fun onNewEmailChange(value: String) = _state.update { it.copy(newEmail = value, emailChangeError = null) }

    fun onCodeChange(value: String) =
        _state.update { it.copy(code = value.filter(Char::isDigit).take(CODE_LENGTH), emailChangeError = null) }

    /** Sends a code to the new address; also used to resend it. */
    fun submitNewEmail() {
        val current = _state.value
        if (current.isEmailChangeBusy) return
        val email = (current.pendingEmail ?: current.newEmail).trim()
        Validators.email(email, "newEmail")?.let { error ->
            _state.update { it.copy(emailChangeError = error.message) }
            return
        }
        if (email.equals(current.email, ignoreCase = true)) {
            _state.update { it.copy(emailChangeError = "That's already your email") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isEmailChangeBusy = true, emailChangeError = null) }
            when (val result = accountRepository.requestEmailChange(email)) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isEmailChangeBusy = false,
                        emailChangeStep = EmailChangeStep.ENTER_CODE,
                        pendingEmail = result.data.newEmail,
                        code = "",
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isEmailChangeBusy = false, emailChangeError = result.error.emailChangeMessage())
                }
            }
        }
    }

    fun submitCode() {
        val current = _state.value
        if (current.isEmailChangeBusy) return
        if (current.code.length != CODE_LENGTH) {
            _state.update { it.copy(emailChangeError = "Enter the $CODE_LENGTH-digit code") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isEmailChangeBusy = true, emailChangeError = null) }
            when (val result = accountRepository.confirmEmailChange(current.code)) {
                is DataResult.Success -> {
                    sessionManager.onUserLoaded(result.data)
                    _state.update {
                        it.copy(
                            user = result.data,
                            isEmailChangeBusy = false,
                            emailChangeStep = EmailChangeStep.CLOSED,
                            emailChangedTo = result.data.email,
                            pendingEmail = null,
                            code = "",
                        )
                    }
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isEmailChangeBusy = false, emailChangeError = result.error.emailChangeMessage())
                }
            }
        }
    }

    private fun PersonalInfoState.withUser(user: User) = copy(
        user = user,
        fullName = user.displayName,
        phone = user.phoneNumber.orEmpty(),
        currency = user.defaultCurrency.takeIf { it in PersonalInfoState.currencyChoices } ?: CurrencyCode.NGN,
    )

    private fun PersonalInfoState.withError(error: DataError) = when (error) {
        is DataError.Validation -> copy(fieldErrors = error.fieldErrors.associate { it.field to it.message })
        else -> copy(errorMessage = error.userMessage())
    }

    private fun DataError.emailChangeMessage(): String = when {
        this is DataError.Api && code == ApiErrorCode.EMAIL_ALREADY_REGISTERED ->
            "Another account already uses that email"
        else -> userMessage()
    }

    private companion object {
        const val CODE_LENGTH = 6
    }
}
