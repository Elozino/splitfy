package com.elozino.splitfy.screens.account.security_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.DeviceSession
import com.elozino.splitfy.repositories.SecurityRepository
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    val sessions: List<DeviceSession> = emptyList(),
    /** Waiting for the user to confirm signing this device out. */
    val pendingRevoke: DeviceSession? = null,
    val isConfirmingRevokeOthers: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null,
) {
    val current: DeviceSession? get() = sessions.firstOrNull { it.current }
    val others: List<DeviceSession> get() = sessions.filterNot { it.current }
}

class DevicesViewModel(private val security: SecurityRepository) : ViewModel() {

    private val _state = MutableStateFlow(DevicesState())
    val state: StateFlow<DevicesState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = fetch(refreshing = false)

    fun refresh() = fetch(refreshing = true)

    private fun fetch(refreshing: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !refreshing && it.sessions.isEmpty(), isRefreshing = refreshing, loadError = null) }
            when (val result = security.sessions()) {
                is DataResult.Success -> _state.update {
                    it.copy(isLoading = false, isRefreshing = false, sessions = result.data)
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadError = result.error.userMessage())
                }
            }
        }
    }

    fun askToRevoke(session: DeviceSession?) = _state.update { it.copy(pendingRevoke = session?.takeUnless { s -> s.current }) }

    fun confirmRevoke() {
        val session = _state.value.pendingRevoke ?: return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            when (val result = security.revokeSession(session.id)) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isWorking = false,
                        pendingRevoke = null,
                        sessions = it.sessions.filterNot { s -> s.id == session.id },
                        message = "${session.displayName} was signed out",
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isWorking = false, pendingRevoke = null, message = result.error.userMessage())
                }
            }
        }
    }

    fun askToRevokeOthers(visible: Boolean) = _state.update { it.copy(isConfirmingRevokeOthers = visible) }

    fun confirmRevokeOthers() {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            when (val result = security.revokeOtherSessions()) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        isWorking = false,
                        isConfirmingRevokeOthers = false,
                        sessions = it.sessions.filter { s -> s.current },
                        message = when (result.data) {
                            0 -> "No other devices were signed in"
                            1 -> "Signed out of 1 other device"
                            else -> "Signed out of ${result.data} other devices"
                        },
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isWorking = false, isConfirmingRevokeOthers = false, message = result.error.userMessage())
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** "Pixel 8", or something sensible for a session from before devices were named. */
val DeviceSession.displayName: String
    get() = deviceName?.takeIf { it.isNotBlank() } ?: when (platform) {
        com.elozino.splitfy.api.dto.DevicePlatform.ANDROID -> "Android device"
        com.elozino.splitfy.api.dto.DevicePlatform.IOS -> "iPhone or iPad"
        null -> "Unknown device"
    }
