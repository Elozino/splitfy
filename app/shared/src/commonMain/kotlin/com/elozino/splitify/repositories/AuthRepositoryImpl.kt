package com.elozino.splitify.repositories

import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.api.dto.AuthResponse
import com.elozino.splitify.api.dto.ForgotPasswordRequest
import com.elozino.splitify.api.dto.LoginRequest
import com.elozino.splitify.api.dto.LogoutRequest
import com.elozino.splitify.api.dto.MessageResponse
import com.elozino.splitify.api.dto.RegisterRequest
import com.elozino.splitify.api.dto.ResetPasswordRequest
import com.elozino.splitify.api.dto.VerifyOtpRequest
import com.elozino.splitify.api.dto.VerifyOtpResponse
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.User
import com.elozino.splitify.map
import com.elozino.splitify.network.ApiCaller
import com.elozino.splitify.onSuccess
import com.elozino.splitify.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class AuthRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
    private val session: SessionManager,
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

    /**
     * Local state is cleared regardless of the server's answer: a user who taps
     * "sign out" while offline must still end up signed out on the device.
     */
    override suspend fun logout(): DataResult<Unit> {
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
}
