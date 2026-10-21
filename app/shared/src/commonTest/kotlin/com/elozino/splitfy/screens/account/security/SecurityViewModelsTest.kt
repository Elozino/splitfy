package com.elozino.splitfy.screens.account.security

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.FieldError
import com.elozino.splitfy.api.dto.DevicePlatform
import com.elozino.splitfy.api.dto.DeviceSession
import com.elozino.splitfy.api.dto.SecurityOverview
import com.elozino.splitfy.screens.account.security_settings.ChangePasswordViewModel
import com.elozino.splitfy.screens.account.security_settings.DevicesViewModel
import com.elozino.splitfy.screens.account.security_settings.SecurityViewModel
import com.elozino.splitfy.screens.account.security_settings.TwoFactorSetupViewModel
import com.elozino.splitfy.security.AppLockController
import com.elozino.splitfy.security.AppLockSettings
import com.elozino.splitfy.storage.SessionStorage
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val security = FakeSecurityRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // --- Change password ------------------------------------------------------

    @Test
    fun `mismatched or weak passwords never reach the server`() = runTest(dispatcher) {
        val viewModel = ChangePasswordViewModel(hasPassword = true, security)
        viewModel.onCurrentPasswordChange("Password123")
        viewModel.onNewPasswordChange("short")
        viewModel.onConfirmPasswordChange("different")

        viewModel.submit()
        advanceUntilIdle()

        val errors = viewModel.state.value.fieldErrors
        assertTrue(ChangePasswordViewModel.FIELD_NEW in errors)
        assertEquals("Passwords don't match", errors[ChangePasswordViewModel.FIELD_CONFIRM])
        assertTrue(security.passwordChanges.isEmpty())
    }

    @Test
    fun `a wrong current password shows against that field`() = runTest(dispatcher) {
        security.changePasswordResult = DataResult.Failure(
            DataError.Validation(listOf(FieldError("currentPassword", "Your current password is incorrect")), "nope"),
        )
        val viewModel = filledChangePassword(hasPassword = true)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Your current password is incorrect", viewModel.state.value.fieldErrors["currentPassword"])
        assertFalse(viewModel.state.value.isDone)
    }

    @Test
    fun `a successful change finishes and forgets the passwords`() = runTest(dispatcher) {
        val viewModel = filledChangePassword(hasPassword = true)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf<Pair<String?, String>>("Password123" to "NewPassword456"), security.passwordChanges)
        assertTrue(viewModel.state.value.isDone)
        assertEquals("", viewModel.state.value.newPassword)
        assertEquals("", viewModel.state.value.currentPassword)
    }

    @Test
    fun `setting a first password sends no current password`() = runTest(dispatcher) {
        val viewModel = ChangePasswordViewModel(hasPassword = false, security)
        viewModel.onNewPasswordChange("NewPassword456")
        viewModel.onConfirmPasswordChange("NewPassword456")
        assertTrue(viewModel.state.value.canSubmit)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf<Pair<String?, String>>(null to "NewPassword456"), security.passwordChanges)
    }

    private fun filledChangePassword(hasPassword: Boolean) = ChangePasswordViewModel(hasPassword, security).apply {
        onCurrentPasswordChange("Password123")
        onNewPasswordChange("NewPassword456")
        onConfirmPasswordChange("NewPassword456")
    }

    // --- Devices --------------------------------------------------------------

    private fun session(id: String, current: Boolean = false) = DeviceSession(
        id = id,
        deviceName = "Device $id",
        platform = DevicePlatform.ANDROID,
        createdAt = Instant.fromEpochSeconds(0),
        lastUsedAt = Instant.fromEpochSeconds(60),
        current = current,
    )

    @Test
    fun `devices split into this one and the others`() = runTest(dispatcher) {
        security.sessionsResult = DataResult.Success(listOf(session("a", current = true), session("b"), session("c")))

        val viewModel = DevicesViewModel(security)
        advanceUntilIdle()

        assertEquals("a", viewModel.state.value.current?.id)
        assertEquals(listOf("b", "c"), viewModel.state.value.others.map { it.id })
    }

    @Test
    fun `revoking asks first and then removes the device`() = runTest(dispatcher) {
        security.sessionsResult = DataResult.Success(listOf(session("a", current = true), session("b")))
        val viewModel = DevicesViewModel(security)
        advanceUntilIdle()

        viewModel.askToRevoke(viewModel.state.value.others.single())
        assertTrue(security.revoked.isEmpty())
        viewModel.confirmRevoke()
        advanceUntilIdle()

        assertEquals(listOf("b"), security.revoked)
        assertEquals(listOf("a"), viewModel.state.value.sessions.map { it.id })
        assertNull(viewModel.state.value.pendingRevoke)
    }

    @Test
    fun `this device cannot be picked for revoking`() = runTest(dispatcher) {
        security.sessionsResult = DataResult.Success(listOf(session("a", current = true)))
        val viewModel = DevicesViewModel(security)
        advanceUntilIdle()

        viewModel.askToRevoke(viewModel.state.value.current)

        assertNull(viewModel.state.value.pendingRevoke)
    }

    @Test
    fun `signing out everywhere else keeps only this device`() = runTest(dispatcher) {
        security.sessionsResult = DataResult.Success(listOf(session("a", current = true), session("b"), session("c")))
        security.revokeOthersResult = DataResult.Success(2)
        val viewModel = DevicesViewModel(security)
        advanceUntilIdle()

        viewModel.confirmRevokeOthers()
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.state.value.sessions.map { it.id })
        assertEquals("Signed out of 2 other devices", viewModel.state.value.message)
    }

    @Test
    fun `a failed load shows an error`() = runTest(dispatcher) {
        security.sessionsResult = DataResult.Failure(DataError.Offline)
        val viewModel = DevicesViewModel(security)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadError!!.contains("offline"))
    }

    // --- Two-step setup -------------------------------------------------------

    @Test
    fun `enrolment shows the secret and then the recovery codes after a right code`() = runTest(dispatcher) {
        val viewModel = TwoFactorSetupViewModel(security)
        advanceUntilIdle()
        assertEquals("JBSWY3DPEHPK3PXP", viewModel.state.value.setup?.secret)

        viewModel.onCodeChange("12a34 56")
        assertEquals("123456", viewModel.state.value.code)
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf("123456"), security.enableCodes)
        assertTrue(viewModel.state.value.isEnabled)
        assertEquals(10, viewModel.state.value.recoveryCodes.size)
    }

    @Test
    fun `a wrong enrolment code clears for another try`() = runTest(dispatcher) {
        security.enableResult = DataResult.Failure(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, "That code isn't right."))
        val viewModel = TwoFactorSetupViewModel(security)
        advanceUntilIdle()

        viewModel.onCodeChange("000000")
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals("That code isn't right.", viewModel.state.value.errorMessage)
        assertEquals("", viewModel.state.value.code)
        assertFalse(viewModel.state.value.isEnabled)
    }

    @Test
    fun `an unverified email explains why setup can't start`() = runTest(dispatcher) {
        security.setupResult = DataResult.Failure(DataError.Forbidden("Verify your email address before turning on two-step verification"))
        val viewModel = TwoFactorSetupViewModel(security)
        advanceUntilIdle()

        assertEquals("Verify your email address before turning on two-step verification", viewModel.state.value.loadError)
    }

    // --- Security overview ----------------------------------------------------

    private fun securityViewModel(settings: AppLockSettings = AppLockSettings(MapSettings())) =
        SecurityViewModel(security, AppLockController(settings, SessionManager(SessionStorage(MapSettings()))))

    @Test
    fun `turning 2FA off with the password updates the overview`() = runTest(dispatcher) {
        security.overviewResult = DataResult.Success(SecurityOverview(hasPassword = true, twoFactorEnabled = true, recoveryCodesRemaining = 8))
        val viewModel = securityViewModel()
        advanceUntilIdle()

        viewModel.showDisableDialog(true)
        viewModel.toggleDisableMethod()
        viewModel.onDisableInputChange("Password123")
        viewModel.disableTwoFactor()
        advanceUntilIdle()

        assertEquals(listOf<Pair<String?, String?>>(null to "Password123"), security.disables)
        assertFalse(viewModel.state.value.overview!!.twoFactorEnabled)
        assertFalse(viewModel.state.value.isDisableDialogVisible)
    }

    @Test
    fun `a wrong code keeps the dialog open with the reason`() = runTest(dispatcher) {
        security.overviewResult = DataResult.Success(SecurityOverview(hasPassword = true, twoFactorEnabled = true))
        security.disableResult = DataResult.Failure(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, "That code isn't right."))
        val viewModel = securityViewModel()
        advanceUntilIdle()

        viewModel.showDisableDialog(true)
        viewModel.onDisableInputChange(" 123456 ")
        viewModel.disableTwoFactor()
        advanceUntilIdle()

        assertEquals(listOf<Pair<String?, String?>>("123456" to null), security.disables)
        assertTrue(viewModel.state.value.isDisableDialogVisible)
        assertEquals("That code isn't right.", viewModel.state.value.disableError)
        assertTrue(viewModel.state.value.overview!!.twoFactorEnabled)
    }

    @Test
    fun `app lock choices are stored on the device`() = runTest(dispatcher) {
        val storage = MapSettings()
        val viewModel = securityViewModel(AppLockSettings(storage))

        viewModel.setAppLockEnabled(true)
        viewModel.setAppLockTimeout(5)

        val reloaded = AppLockSettings(storage)
        assertTrue(reloaded.enabled.value)
        assertEquals(5, reloaded.timeoutMinutes.value)
    }
}
