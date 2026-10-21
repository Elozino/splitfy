package com.elozino.splitfy.repositories

import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.ChangePasswordRequest
import com.elozino.splitfy.api.dto.DeviceSession
import com.elozino.splitfy.api.dto.MessageResponse
import com.elozino.splitfy.api.dto.RecoveryCodesResponse
import com.elozino.splitfy.api.dto.RevokedSessionsResponse
import com.elozino.splitfy.api.dto.SecurityOverview
import com.elozino.splitfy.api.dto.TwoFactorDisableRequest
import com.elozino.splitfy.api.dto.TwoFactorEnableRequest
import com.elozino.splitfy.api.dto.TwoFactorSetupResponse
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.map
import com.elozino.splitfy.network.ApiCaller
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/** How a sign-in attempt ended: signed in, or one step short because the account has 2FA. */
sealed interface SignInResult {
    data class SignedIn(val user: User) : SignInResult

    /** The first factor was right. Send [challengeToken] with a code within [expiresInSeconds]. */
    data class TwoFactorRequired(val challengeToken: String, val expiresInSeconds: Long) : SignInResult
}

/** The signed-in user's password, devices and two-step verification. */
interface SecurityRepository {
    suspend fun overview(): DataResult<SecurityOverview>

    /** [currentPassword] is null when the account has no password of its own yet. */
    suspend fun changePassword(currentPassword: String?, newPassword: String): DataResult<Unit>

    suspend fun sessions(): DataResult<List<DeviceSession>>
    suspend fun revokeSession(id: String): DataResult<Unit>

    /** How many other devices were signed out. */
    suspend fun revokeOtherSessions(): DataResult<Int>

    suspend fun startTwoFactorSetup(): DataResult<TwoFactorSetupResponse>

    /** Returns the recovery codes, which the server will never show again. */
    suspend fun enableTwoFactor(code: String): DataResult<List<String>>

    suspend fun disableTwoFactor(code: String?, password: String?): DataResult<Unit>
}

class SecurityRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : SecurityRepository {

    override suspend fun overview(): DataResult<SecurityOverview> =
        caller.execute { client.get(ApiRoutes.Security.BASE) }

    override suspend fun changePassword(currentPassword: String?, newPassword: String): DataResult<Unit> =
        caller.execute<MessageResponse> {
            client.post(ApiRoutes.Security.PASSWORD) { setBody(ChangePasswordRequest(currentPassword, newPassword)) }
        }.map { }

    override suspend fun sessions(): DataResult<List<DeviceSession>> =
        caller.execute { client.get(ApiRoutes.Security.SESSIONS) }

    override suspend fun revokeSession(id: String): DataResult<Unit> =
        caller.execute<Unit> { client.delete(ApiRoutes.Security.session(id)) }

    override suspend fun revokeOtherSessions(): DataResult<Int> =
        caller.execute<RevokedSessionsResponse> { client.post(ApiRoutes.Security.REVOKE_OTHER_SESSIONS) }
            .map { it.revoked }

    override suspend fun startTwoFactorSetup(): DataResult<TwoFactorSetupResponse> =
        caller.execute { client.post(ApiRoutes.Security.TWO_FACTOR_SETUP) }

    override suspend fun enableTwoFactor(code: String): DataResult<List<String>> =
        caller.execute<RecoveryCodesResponse> {
            client.post(ApiRoutes.Security.TWO_FACTOR_ENABLE) { setBody(TwoFactorEnableRequest(code)) }
        }.map { it.recoveryCodes }

    override suspend fun disableTwoFactor(code: String?, password: String?): DataResult<Unit> =
        caller.execute<MessageResponse> {
            client.post(ApiRoutes.Security.TWO_FACTOR_DISABLE) { setBody(TwoFactorDisableRequest(code, password)) }
        }.map { }
}
