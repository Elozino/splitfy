package com.elozino.splitfy.screens.account

import com.elozino.splitfy.DataError
import com.elozino.splitfy.api.dto.LinkedIdentity
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.screens.account.linked_accounts.LinkedAccountsViewModel
import com.elozino.splitfy.social.SocialSignInResult
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
class LinkedAccountsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = FakeAccounts()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a token from the sign-in sheet links the provider`() = runTest(dispatcher) {
        val vm = LinkedAccountsViewModel(accounts)
        advanceUntilIdle()

        vm.onLinkStarted(OAuthProvider.GOOGLE)
        assertEquals(OAuthProvider.GOOGLE, vm.state.value.busyProvider)
        vm.onSignInResult(OAuthProvider.GOOGLE, SocialSignInResult.Success("id-token"))
        advanceUntilIdle()

        assertEquals(OAuthProvider.GOOGLE to "id-token", accounts.linkCalls.single())
        assertEquals("ada@gmail.com", vm.state.value.identity(OAuthProvider.GOOGLE)?.email)
        assertNull(vm.state.value.busyProvider)
    }

    @Test
    fun `cancelling the sheet does nothing and a refusal is shown`() = runTest(dispatcher) {
        val vm = LinkedAccountsViewModel(accounts)
        advanceUntilIdle()

        vm.onLinkStarted(OAuthProvider.GOOGLE)
        vm.onSignInResult(OAuthProvider.GOOGLE, SocialSignInResult.Cancelled)
        assertNull(vm.state.value.busyProvider)
        assertNull(vm.state.value.errorMessage)

        accounts.linkFailure = DataError.Api(com.elozino.splitfy.api.ApiErrorCode.CONFLICT, "Linked to another account")
        vm.onSignInResult(OAuthProvider.GOOGLE, SocialSignInResult.Success("id-token"))
        advanceUntilIdle()
        assertEquals("Linked to another account", vm.state.value.errorMessage)
        assertNull(vm.state.value.identity(OAuthProvider.GOOGLE))
    }

    @Test
    fun `the last way in cannot be unlinked`() = runTest(dispatcher) {
        accounts.linked = accounts.linked.copy(
            hasPassword = false,
            identities = listOf(LinkedIdentity(OAuthProvider.GOOGLE, "ada@gmail.com", epoch)),
        )
        val vm = LinkedAccountsViewModel(accounts)
        advanceUntilIdle()
        assertFalse(vm.state.value.canUnlink(OAuthProvider.GOOGLE))

        vm.onSignInResult(OAuthProvider.APPLE, SocialSignInResult.Success("apple-token"))
        advanceUntilIdle()
        assertTrue(vm.state.value.canUnlink(OAuthProvider.GOOGLE))

        vm.unlink(OAuthProvider.GOOGLE)
        advanceUntilIdle()
        assertNull(vm.state.value.identity(OAuthProvider.GOOGLE))
        assertFalse(vm.state.value.canUnlink(OAuthProvider.APPLE))
    }

    @Test
    fun `rows are what both sides support plus anything already linked`() = runTest(dispatcher) {
        accounts.linked = accounts.linked.copy(
            availableProviders = listOf(OAuthProvider.GOOGLE),
            identities = listOf(LinkedIdentity(OAuthProvider.APPLE, null, epoch)),
        )
        val vm = LinkedAccountsViewModel(accounts)
        advanceUntilIdle()

        val androidProviders = listOf(OAuthProvider.GOOGLE)
        assertEquals(listOf(OAuthProvider.GOOGLE, OAuthProvider.APPLE), vm.state.value.providers(androidProviders))
        assertFalse(vm.state.value.canLink(OAuthProvider.APPLE, androidProviders))
        assertEquals(emptyList(), vm.state.value.providers(emptyList()).filter { it == OAuthProvider.GOOGLE })
    }
}
