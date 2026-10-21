package com.elozino.splitfy.screens.account

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.screens.account.data_analytics.DataAnalyticsViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DataAnalyticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = FakeAccounts()
    private val crash = FakeCrashReporting(appliesImmediately = false)
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-21T09:00:00Z")
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the crash report switch is the device setting`() {
        val vm = DataAnalyticsViewModel(accounts, crash, clock)
        assertTrue(vm.state.value.crashReportsAvailable)
        assertTrue(vm.state.value.crashReportsEnabled)
        assertTrue(vm.state.value.crashReportsApplyOnRestart)

        vm.setCrashReportsEnabled(false)

        assertFalse(crash.isEnabled)
        assertFalse(vm.state.value.crashReportsEnabled)
    }

    @Test
    fun `downloading hands the export to the share sheet once`() = runTest(dispatcher) {
        val vm = DataAnalyticsViewModel(accounts, crash, clock)

        vm.downloadData()
        advanceUntilIdle()

        val file = vm.state.value.exportToShare!!
        assertEquals("splitfy-data-2026-10-21.json", file.fileName)
        assertEquals("""{"format":"splitfy-export/1"}""", file.bytes.decodeToString())
        vm.onExportShared()
        assertNull(vm.state.value.exportToShare)
    }

    @Test
    fun `a failed download says why`() = runTest(dispatcher) {
        accounts.exportResult = DataResult.Failure(DataError.Offline)
        val vm = DataAnalyticsViewModel(accounts, crash, clock)

        vm.downloadData()
        advanceUntilIdle()

        assertNull(vm.state.value.exportToShare)
        assertFalse(vm.state.value.isExporting)
        assertEquals("You're offline. Check your connection and try again.", vm.state.value.errorMessage)
    }
}
