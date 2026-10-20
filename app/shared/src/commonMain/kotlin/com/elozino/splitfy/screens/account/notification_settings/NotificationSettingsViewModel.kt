package com.elozino.splitfy.screens.account.notification_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.NotificationPreferences
import com.elozino.splitfy.api.dto.UpdateNotificationPreferencesRequest
import com.elozino.splitfy.push.PushRepository
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One switch on the screen, mapped to a field of [NotificationPreferences]. */
enum class NotificationSetting(val label: String, val description: String) {
    NEW_EXPENSES("New expenses", "When someone adds an expense to one of your groups"),
    SETTLEMENTS("Payments", "When a payment to or from you is recorded or confirmed"),
    GROUP_ACTIVITY("Group activity", "When someone joins a group with your invite"),
    PAYMENT_REMINDERS("Payment reminders", "A nudge when you've owed money for a few days");

    fun read(preferences: NotificationPreferences): Boolean = when (this) {
        NEW_EXPENSES -> preferences.newExpenses
        SETTLEMENTS -> preferences.settlements
        GROUP_ACTIVITY -> preferences.groupActivity
        PAYMENT_REMINDERS -> preferences.paymentReminders
    }

    fun write(preferences: NotificationPreferences, on: Boolean): NotificationPreferences = when (this) {
        NEW_EXPENSES -> preferences.copy(newExpenses = on)
        SETTLEMENTS -> preferences.copy(settlements = on)
        GROUP_ACTIVITY -> preferences.copy(groupActivity = on)
        PAYMENT_REMINDERS -> preferences.copy(paymentReminders = on)
    }

    fun request(on: Boolean): UpdateNotificationPreferencesRequest = when (this) {
        NEW_EXPENSES -> UpdateNotificationPreferencesRequest(newExpenses = on)
        SETTLEMENTS -> UpdateNotificationPreferencesRequest(settlements = on)
        GROUP_ACTIVITY -> UpdateNotificationPreferencesRequest(groupActivity = on)
        PAYMENT_REMINDERS -> UpdateNotificationPreferencesRequest(paymentReminders = on)
    }
}

data class NotificationSettingsState(
    val isLoading: Boolean = true,
    val preferences: NotificationPreferences? = null,
    val errorMessage: String? = null,
)

class NotificationSettingsViewModel(
    private val repository: PushRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSettingsState())
    val state: StateFlow<NotificationSettingsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.preferences()) {
                is DataResult.Success -> _state.update { it.copy(isLoading = false, preferences = result.data) }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /** Optimistic: the switch moves at once and moves back if the server refuses. */
    fun toggle(setting: NotificationSetting, on: Boolean) {
        val before = _state.value.preferences ?: return
        _state.update { it.copy(preferences = setting.write(before, on)) }
        viewModelScope.launch {
            when (val result = repository.updatePreferences(setting.request(on))) {
                is DataResult.Success -> _state.update { it.copy(preferences = result.data) }
                is DataResult.Failure -> _state.update {
                    it.copy(preferences = setting.write(it.preferences ?: before, !on), errorMessage = result.error.userMessage())
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
}
