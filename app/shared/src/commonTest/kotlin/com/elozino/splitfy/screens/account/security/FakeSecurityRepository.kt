package com.elozino.splitfy.screens.account.security

import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.DeviceSession
import com.elozino.splitfy.api.dto.SecurityOverview
import com.elozino.splitfy.api.dto.TwoFactorSetupResponse
import com.elozino.splitfy.repositories.SecurityRepository

/** Answers from fields the test sets; records what was asked. */
class FakeSecurityRepository : SecurityRepository {
    var overviewResult: DataResult<SecurityOverview> = DataResult.Success(SecurityOverview(hasPassword = true, twoFactorEnabled = false))
    var changePasswordResult: DataResult<Unit> = DataResult.Success(Unit)
    var sessionsResult: DataResult<List<DeviceSession>> = DataResult.Success(emptyList())
    var revokeResult: DataResult<Unit> = DataResult.Success(Unit)
    var revokeOthersResult: DataResult<Int> = DataResult.Success(0)
    var setupResult: DataResult<TwoFactorSetupResponse> =
        DataResult.Success(TwoFactorSetupResponse("JBSWY3DPEHPK3PXP", "otpauth://totp/splitfy:ada?secret=JBSWY3DPEHPK3PXP"))
    var enableResult: DataResult<List<String>> = DataResult.Success(List(10) { "code$it-abcde" })
    var disableResult: DataResult<Unit> = DataResult.Success(Unit)

    val passwordChanges = mutableListOf<Pair<String?, String>>()
    val revoked = mutableListOf<String>()
    val enableCodes = mutableListOf<String>()
    val disables = mutableListOf<Pair<String?, String?>>()

    override suspend fun overview() = overviewResult
    override suspend fun changePassword(currentPassword: String?, newPassword: String): DataResult<Unit> {
        passwordChanges += currentPassword to newPassword
        return changePasswordResult
    }
    override suspend fun sessions() = sessionsResult
    override suspend fun revokeSession(id: String): DataResult<Unit> {
        revoked += id
        return revokeResult
    }
    override suspend fun revokeOtherSessions() = revokeOthersResult
    override suspend fun startTwoFactorSetup() = setupResult
    override suspend fun enableTwoFactor(code: String): DataResult<List<String>> {
        enableCodes += code
        return enableResult
    }
    override suspend fun disableTwoFactor(code: String?, password: String?): DataResult<Unit> {
        disables += code to password
        return disableResult
    }
}
