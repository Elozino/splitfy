package com.elozino.splitfy.screens.auth

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.repositories.AuthRepository
import com.elozino.splitfy.repositories.SignInResult
import com.elozino.splitfy.screens.auth.shared.AuthViewModel
import com.elozino.splitfy.social.SocialSignInResult
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
class TwoFactorSignInTest {

    private val dispatcher = StandardTestDispatcher()
    private val user = User(id = UserId("u1"), email = "ada@example.com", displayName = "Ada", createdAt = Instant.fromEpochSeconds(0))

    private class FakeAuth(private val user: User) : AuthRepository {
        var signIn: DataResult<SignInResult> = DataResult.Success(SignInResult.TwoFactorRequired("challenge-1", 300))
        var verify: DataResult<User> = DataResult.Success(user)
        val verified = mutableListOf<Pair<String, String>>()

        override suspend fun register(email: String, password: String, displayName: String, phoneNumber: String?) = DataResult.Success(user)
        override suspend fun login(email: String, password: String) = signIn
        override suspend fun verifyTwoFactor(challengeToken: String, code: String): DataResult<User> {
            verified += challengeToken to code
            return verify
        }
        override suspend fun requestPasswordReset(email: String) = DataResult.Success(Unit)
        override suspend fun verifyOtp(email: String, code: String) = DataResult.Success("reset")
        override suspend fun resetPassword(email: String, resetToken: String, newPassword: String) = DataResult.Success(Unit)
        override suspend fun verifyEmail(code: String) = DataResult.Success(user)
        override suspend fun resendVerification() = DataResult.Success(Unit)
        override suspend fun signInWithProvider(provider: OAuthProvider, request: OAuthSignInRequest) = signIn
        override suspend fun logout() = DataResult.Success(Unit)
        override suspend fun deleteAccount() = DataResult.Success(Unit)
    }

    private val auth = FakeAuth(user)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AuthViewModel(auth, SessionManager(SessionStorage(MapSettings())))

    private fun AuthViewModel.typeCredentials() {
        onEmailChange("ada@example.com")
        onPasswordChange("Password123")
    }

    @Test
    fun `a challenge parks sign-in on the code step`() = runTest(dispatcher) {
        val viewModel = viewModel().apply { typeCredentials() }

        viewModel.login()
        advanceUntilIdle()

        assertEquals("challenge-1", viewModel.state.value.twoFactorChallenge)
        assertFalse(viewModel.state.value.isAuthenticated)
        assertEquals("", viewModel.state.value.password, "the password is dropped once it has been used")
    }

    @Test
    fun `the right code finishes sign-in`() = runTest(dispatcher) {
        val viewModel = viewModel().apply { typeCredentials() }
        viewModel.login()
        advanceUntilIdle()

        viewModel.onTwoFactorCodeChange("12 34-56")
        assertEquals("123456", viewModel.state.value.twoFactorCode)
        viewModel.verifyTwoFactor()
        advanceUntilIdle()

        assertEquals(listOf("challenge-1" to "123456"), auth.verified)
        assertTrue(viewModel.state.value.isAuthenticated)
        assertNull(viewModel.state.value.twoFactorChallenge)
    }

    @Test
    fun `a wrong code is cleared with the server's reason`() = runTest(dispatcher) {
        auth.verify = DataResult.Failure(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, "That code isn't right."))
        val viewModel = viewModel().apply { typeCredentials() }
        viewModel.login()
        advanceUntilIdle()

        viewModel.onTwoFactorCodeChange("000000")
        viewModel.verifyTwoFactor()
        advanceUntilIdle()

        assertEquals("That code isn't right.", viewModel.state.value.errorMessage)
        assertEquals("", viewModel.state.value.twoFactorCode)
        assertEquals("challenge-1", viewModel.state.value.twoFactorChallenge)
    }

    @Test
    fun `a recovery code can be typed instead`() = runTest(dispatcher) {
        val viewModel = viewModel().apply { typeCredentials() }
        viewModel.login()
        advanceUntilIdle()

        viewModel.toggleRecoveryCode()
        viewModel.onTwoFactorCodeChange("abcde-fghjk")
        assertTrue(viewModel.state.value.canSubmitTwoFactor)
        viewModel.verifyTwoFactor()
        advanceUntilIdle()

        assertEquals(listOf("challenge-1" to "abcde-fghjk"), auth.verified)
    }

    @Test
    fun `Google sign-in also stops at the code step`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSocialSignIn(OAuthProvider.GOOGLE, SocialSignInResult.Success(idToken = "id-token"))
        advanceUntilIdle()

        assertEquals("challenge-1", viewModel.state.value.twoFactorChallenge)
        assertFalse(viewModel.state.value.isAuthenticated)
    }

    @Test
    fun `without 2FA a login signs straight in`() = runTest(dispatcher) {
        auth.signIn = DataResult.Success(SignInResult.SignedIn(user))
        val viewModel = viewModel().apply { typeCredentials() }

        viewModel.login()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isAuthenticated)
        assertNull(viewModel.state.value.twoFactorChallenge)
    }
}
