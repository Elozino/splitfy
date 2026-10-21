package com.elozino.splitfy.repositories

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AuthResponse
import com.elozino.splitfy.api.dto.ForgotPasswordRequest
import com.elozino.splitfy.api.dto.LoginRequest
import com.elozino.splitfy.api.dto.LogoutRequest
import com.elozino.splitfy.api.dto.MessageResponse
import com.elozino.splitfy.api.dto.RegisterRequest
import com.elozino.splitfy.api.dto.ResetPasswordRequest
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.api.dto.VerifyEmailRequest
import com.elozino.splitfy.api.dto.VerifyOtpRequest
import com.elozino.splitfy.api.dto.VerifyOtpResponse
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.map
import com.elozino.splitfy.network.ApiCaller
import com.elozino.splitfy.onSuccess
import com.elozino.splitfy.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class AuthRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
    private val session: SessionManager,
    /** Runs before sign-out while requests can still authenticate (unregistering push). */
    private val beforeLogout: suspend () -> Unit = {},
) : AuthRepository {

    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
        phoneNumber: String?,
    ): DataResult<User> = caller.execute<AuthResponse> {
        client.post(ApiRoutes.Auth.REGISTER) {
            setBody(RegisterRequest(email, password, displayName, phoneNumber))
        }
    }.onSuccess { session.onAuthenticated(it.user, it.tokens) }.map { it.user }

    override suspend fun login(email: String, password: String): DataResult<User> =
        caller.execute<AuthResponse> {
            client.post(ApiRoutes.Auth.LOGIN) { setBody(LoginRequest(email, password)) }
        }.onSuccess { session.onAuthenticated(it.user, it.tokens) }.map { it.user }

    override suspend fun requestPasswordReset(email: String): DataResult<Unit> =
        caller.execute<MessageResponse> {
            client.post(ApiRoutes.Auth.FORGOT_PASSWORD) { setBody(ForgotPasswordRequest(email)) }
        }.map { }

    override suspend fun verifyOtp(email: String, code: String): DataResult<String> =
        caller.execute<VerifyOtpResponse> {
            client.post(ApiRoutes.Auth.VERIFY_OTP) { setBody(VerifyOtpRequest(email, code)) }
        }.map { it.resetToken }

    override suspend fun resetPassword(
        email: String,
        resetToken: String,
        newPassword: String,
    ): DataResult<Unit> = caller.execute<MessageResponse> {
        client.post(ApiRoutes.Auth.RESET_PASSWORD) {
            setBody(ResetPasswordRequest(email, resetToken, newPassword))
        }
    }.map { }

    override suspend fun verifyEmail(code: String): DataResult<User> =
        caller.execute<User> {
            client.post(ApiRoutes.Auth.VERIFY_EMAIL) { setBody(VerifyEmailRequest(code)) }
        }.onSuccess(session::onUserLoaded)

    override suspend fun signInWithProvider(provider: OAuthProvider, request: OAuthSignInRequest): DataResult<User> =
        caller.execute<AuthResponse> {
            client.post(ApiRoutes.Auth.oauth(provider.path)) { setBody(request) }
        }.onSuccess { session.onAuthenticated(it.user, it.tokens) }.map { it.user }

    override suspend fun resendVerification(): DataResult<Unit> =
        caller.execute<MessageResponse> { client.post(ApiRoutes.Auth.RESEND_VERIFICATION) }.map { }

    /**
     * Local state is cleared regardless of the server's answer: a user who taps
     * "sign out" while offline must still end up signed out on the device.
     */
    override suspend fun logout(): DataResult<Unit> {
        runCatching { beforeLogout() }
        val refreshToken = session.currentTokens()?.refreshToken
        val result = if (refreshToken != null) {
            caller.execute<MessageResponse> {
                client.post(ApiRoutes.Auth.LOGOUT) { setBody(LogoutRequest(refreshToken)) }
            }.map { }
        } else {
            DataResult.Success(Unit)
        }
        session.signOut()
        return result
    }

    override suspend fun deleteAccount(): DataResult<Unit> =
        // The server has already revoked every session, so there is nothing to log out of.
        caller.execute<Unit> { client.delete(ApiRoutes.Users.ME) }.onSuccess { session.signOut() }
}
