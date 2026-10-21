package com.elozino.splitfy.screens.account

import com.elozino.splitfy.DataError
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.SessionState
import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.TokenPair
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.screens.account.personal_info.EmailChangeStep
import com.elozino.splitfy.screens.account.personal_info.PersonalInfoViewModel
import com.elozino.splitfy.storage.SessionStorage
import com.elozino.splitfy.uploads.FakeUploadRepository
import com.elozino.splitfy.uploads.ImageCompressor
import com.elozino.splitfy.uploads.MarkingCompressor
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
class PersonalInfoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val users = FakeUsers()
    private val accounts = FakeAccounts(users)
    private val uploads = FakeUploadRepository().apply {
        uploadResult = com.elozino.splitfy.DataResult.Success("users/u/avatars/new.jpg")
    }
    private val session = SessionManager(SessionStorage(MapSettings())).apply {
        onAuthenticated(ada, TokenPair("access", "refresh", 900))
    }

    private fun viewModel(cropper: ImageCompressor = MarkingCompressor) =
        PersonalInfoViewModel(users, accounts, uploads, cropper, session, dispatcher)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saving sends name phone and default currency and shares the new profile`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("Ada Lovelace", vm.state.value.fullName)

        vm.onFullNameChange("Ada King")
        vm.onCurrencyChange(CurrencyCode.USD)
        vm.save()
        advanceUntilIdle()

        val sent = users.updates.single()
        assertEquals("Ada King", sent.displayName)
        assertEquals(CurrencyCode.USD, sent.defaultCurrency)
        assertNull(sent.avatarUrl, "Saving the form must not touch the photo")
        assertEquals(1L, vm.state.value.savedAt)
        assertEquals(CurrencyCode.USD, (session.state.value as SessionState.SignedIn).user?.defaultCurrency)
    }

    @Test
    fun `an invalid name is caught before the request`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onFullNameChange("")
        vm.save()
        advanceUntilIdle()

        assertTrue(users.updates.isEmpty())
        assertNotNull(vm.state.value.fieldErrors["displayName"])
    }

    @Test
    fun `a picked photo is cropped and uploaded as an avatar and set on the profile`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAvatarPicked(byteArrayOf(1, 2, 3))
        advanceUntilIdle()

        val upload = uploads.uploads.single()
        assertEquals(UploadPurpose.AVATAR, upload.purpose)
        assertContentEquals(byteArrayOf(1, 2, 3, 0x7F), upload.bytes)
        assertEquals("users/u/avatars/new.jpg", users.updates.single().avatarUrl)
        assertEquals("https://cdn.test/users/u/avatars/new.jpg", vm.state.value.avatarUrl)

        vm.removeAvatar()
        advanceUntilIdle()
        assertEquals("", users.updates.last().avatarUrl)
    }

    @Test
    fun `photos are ignored without uploads and unreadable ones say so`() = runTest(dispatcher) {
        uploads.enabled = false
        val disabled = viewModel()
        advanceUntilIdle()
        disabled.onAvatarPicked(byteArrayOf(1))
        advanceUntilIdle()
        assertTrue(uploads.uploads.isEmpty())

        uploads.enabled = true
        val vm = viewModel(cropper = ImageCompressor { null })
        advanceUntilIdle()
        vm.onAvatarPicked(byteArrayOf(1))
        advanceUntilIdle()
        assertTrue(uploads.uploads.isEmpty())
        assertEquals("That file isn't a photo we can read", vm.state.value.errorMessage)
    }

    @Test
    fun `changing email sends a code to the new address then confirms it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.startEmailChange()
        vm.onNewEmailChange(" Ada.New@example.com ")
        vm.submitNewEmail()
        advanceUntilIdle()
        assertEquals(EmailChangeStep.ENTER_CODE, vm.state.value.emailChangeStep)
        assertEquals("ada.new@example.com", vm.state.value.pendingEmail)

        vm.onCodeChange("000000")
        vm.submitCode()
        advanceUntilIdle()
        assertEquals("Code is not valid or has expired", vm.state.value.emailChangeError)

        vm.onCodeChange("12-34 56")
        vm.submitCode()
        advanceUntilIdle()
        assertEquals(EmailChangeStep.CLOSED, vm.state.value.emailChangeStep)
        assertEquals("ada.new@example.com", vm.state.value.email)
        assertEquals("ada.new@example.com", vm.state.value.emailChangedTo)
        assertEquals("ada.new@example.com", (session.state.value as SessionState.SignedIn).user?.email)
    }

    @Test
    fun `the same or a taken address is refused`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.startEmailChange()
        vm.onNewEmailChange("ADA@example.com")
        vm.submitNewEmail()
        advanceUntilIdle()
        assertEquals("That's already your email", vm.state.value.emailChangeError)
        assertTrue(accounts.emailRequests.isEmpty())

        accounts.requestFailure = DataError.Api(ApiErrorCode.EMAIL_ALREADY_REGISTERED, "taken")
        vm.onNewEmailChange("bob@example.com")
        vm.submitNewEmail()
        advanceUntilIdle()
        assertEquals("Another account already uses that email", vm.state.value.emailChangeError)
        assertEquals(EmailChangeStep.ENTER_EMAIL, vm.state.value.emailChangeStep)
    }
}
