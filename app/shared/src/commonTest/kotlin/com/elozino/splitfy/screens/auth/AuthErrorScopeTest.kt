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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Every auth screen shares one view model; an error must not follow the user to the next screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthErrorScopeTest {

    private val dispatcher = StandardTestDispatcher()
    private val user = User(id = UserId("u1"), email = "ada@example.com", displayName = "Ada", createdAt = Instant.fromEpochSeconds(0))
    private val wrongPassword = DataResult.Failure(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, "Wrong email or password."))

    private inner class FailingAuth : AuthRepository {
        override suspend fun register(email: String, password: String, displayName: String, phoneNumber: String?) = DataResult.Success(user)
        override suspend fun login(email: String, password: String): DataResult<SignInResult> = wrongPassword
        override suspend fun verifyTwoFactor(challengeToken: String, code: String) = DataResult.Success(user)
        override suspend fun requestPasswordReset(email: String) = DataResult.Success(Unit)
        override suspend fun verifyOtp(email: String, code: String) = DataResult.Success("reset")
        override suspend fun resetPassword(email: String, resetToken: String, newPassword: String) = DataResult.Success(Unit)
        override suspend fun verifyEmail(code: String) = DataResult.Success(user)
        override suspend fun resendVerification() = DataResult.Success(Unit)
        override suspend fun signInWithProvider(provider: OAuthProvider, request: OAuthSignInRequest): DataResult<SignInResult> = wrongPassword
        override suspend fun logout() = DataResult.Success(Unit)
        override suspend fun deleteAccount() = DataResult.Success(Unit)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Lets the signed-out session settle first; its first emission resets the form. */
    private fun TestScope.viewModel() =
        AuthViewModel(FailingAuth(), SessionManager(SessionStorage(MapSettings()))).also { advanceUntilIdle() }

    @Test
    fun `a sign-in error on the landing screen is gone on the next screen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSocialSignIn(OAuthProvider.APPLE, SocialSignInResult.Failure("We couldn't sign you in with Apple."))
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.onScreenChanged() // landing → Login

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `field errors from one screen do not show on another`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.login() // empty form
        assertTrue(viewModel.state.value.fieldErrors.isNotEmpty())

        viewModel.onScreenChanged() // Login → Sign up

        assertTrue(viewModel.state.value.fieldErrors.isEmpty())
    }

    @Test
    fun `moving on keeps what the flow carries between screens`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEmailChange("ada@example.com")
        viewModel.onPasswordChange("Password123")
        viewModel.login()
        advanceUntilIdle()
        assertEquals("Wrong email or password.", viewModel.state.value.errorMessage)

        viewModel.onScreenChanged() // Login → Forgot password

        assertNull(viewModel.state.value.errorMessage)
        assertEquals("ada@example.com", viewModel.state.value.email)
    }
}
