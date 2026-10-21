package com.elozino.splitfy.screens.account.data_analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.crash.CrashReportingSettings
import com.elozino.splitfy.repositories.AccountRepository
import com.elozino.splitfy.userMessage
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A downloaded export, waiting for the screen to hand it to the share sheet. */
class ExportFile(val fileName: String, val bytes: ByteArray)

data class DataAnalyticsState(
    val crashReportsAvailable: Boolean = false,
    val crashReportsEnabled: Boolean = true,
    /** On iOS the switch is read at launch, so the screen says so. */
    val crashReportsApplyOnRestart: Boolean = false,
    val isExporting: Boolean = false,
    val exportToShare: ExportFile? = null,
    val errorMessage: String? = null,
)

/** Data & Analytics: "Download my data" and the per-device crash-report switch. */
class DataAnalyticsViewModel(
    private val accountRepository: AccountRepository,
    private val crashReporting: CrashReportingSettings,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DataAnalyticsState(
            crashReportsAvailable = crashReporting.isAvailable,
            crashReportsEnabled = crashReporting.isEnabled,
            crashReportsApplyOnRestart = !crashReporting.appliesImmediately,
        )
    )
    val state: StateFlow<DataAnalyticsState> = _state.asStateFlow()

    fun setCrashReportsEnabled(enabled: Boolean) {
        crashReporting.setEnabled(enabled)
        _state.update { it.copy(crashReportsEnabled = crashReporting.isEnabled) }
    }

    fun downloadData() {
        if (_state.value.isExporting) return
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, errorMessage = null) }
            when (val result = accountRepository.exportData()) {
                is DataResult.Success -> {
                    val date = clock.now().toString().take(10)
                    _state.update {
                        it.copy(isExporting = false, exportToShare = ExportFile("splitfy-data-$date.json", result.data))
                    }
                }
                is DataResult.Failure -> _state.update {
                    it.copy(isExporting = false, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    /** The share sheet has been shown; drop the bytes. */
    fun onExportShared() = _state.update { it.copy(exportToShare = null) }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
}
