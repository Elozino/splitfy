package com.elozino.splitfy.screens.account.security_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.repositories.SecurityRepository
import com.elozino.splitfy.userMessage
import com.elozino.splitfy.validation.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordState(
    /** False for a Google/Apple account with no password of its own: no "current password" field. */
    val hasPassword: Boolean = true,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordsVisible: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && newPassword.isNotEmpty() && confirmPassword.isNotEmpty() &&
            (!hasPassword || currentPassword.isNotEmpty())
}

class ChangePasswordViewModel(
    hasPassword: Boolean,
    private val security: SecurityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordState(hasPassword = hasPassword))
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()

    fun onCurrentPasswordChange(value: String) = _state.update {
        it.copy(currentPassword = value, fieldErrors = it.fieldErrors - FIELD_CURRENT, errorMessage = null)
    }

    fun onNewPasswordChange(value: String) = _state.update {
        it.copy(newPassword = value, fieldErrors = it.fieldErrors - FIELD_NEW, errorMessage = null)
    }

    fun onConfirmPasswordChange(value: String) = _state.update {
        it.copy(confirmPassword = value, fieldErrors = it.fieldErrors - FIELD_CONFIRM, errorMessage = null)
    }

    fun togglePasswordsVisible() = _state.update { it.copy(passwordsVisible = !it.passwordsVisible) }

    /** Same rules as the server (from `:core`), checked before spending a round trip. */
    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        val errors = buildMap {
            if (current.hasPassword && current.currentPassword.isEmpty()) put(FIELD_CURRENT, "Enter your current password")
            Validators.password(current.newPassword, FIELD_NEW)?.let { put(it.field, it.message) }
            if (current.confirmPassword != current.newPassword) put(FIELD_CONFIRM, "Passwords don't match")
            if (current.hasPassword && current.newPassword.isNotEmpty() && current.newPassword == current.currentPassword) {
                put(FIELD_NEW, "Choose a password you haven't used here")
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null, fieldErrors = emptyMap()) }
            val result = security.changePassword(
                currentPassword = current.currentPassword.takeIf { current.hasPassword },
                newPassword = current.newPassword,
            )
            when (result) {
                is DataResult.Success -> _state.update {
                    // Nothing secret lingers in state once it has done its job.
                    it.copy(isSubmitting = false, isDone = true, currentPassword = "", newPassword = "", confirmPassword = "")
                }
                is DataResult.Failure -> _state.update {
                    when (val error = result.error) {
                        is DataError.Validation -> it.copy(
                            isSubmitting = false,
                            fieldErrors = error.fieldErrors.associate { e -> e.field to e.message },
                        )
                        else -> it.copy(isSubmitting = false, errorMessage = error.userMessage())
                    }
                }
            }
        }
    }

    companion object {
        const val FIELD_CURRENT = "currentPassword"
        const val FIELD_NEW = "newPassword"
        const val FIELD_CONFIRM = "confirmPassword"
    }
}
