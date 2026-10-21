package com.elozino.splitfy.screens.account

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.EmailChangePending
import com.elozino.splitfy.api.dto.LinkedAccountsResponse
import com.elozino.splitfy.api.dto.LinkedIdentity
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.crash.CrashReportingSettings
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.repositories.AccountRepository
import com.elozino.splitfy.repositories.UserRepository
import kotlin.time.Instant

internal val epoch = Instant.fromEpochSeconds(0)

internal val ada = User(
    id = UserId("00000000-0000-4000-8000-000000000001"),
    email = "ada@example.com",
    displayName = "Ada Lovelace",
    phoneNumber = "+2348000000000",
    createdAt = epoch,
    emailVerified = true,
)

/** Applies profile updates the way the server does: only fields that were sent change. */
internal class FakeUsers(var current: User = ada) : UserRepository {
    val updates = mutableListOf<UpdateProfileRequest>()
    var updateFailure: DataError? = null

    override suspend fun me(): DataResult<User> = DataResult.Success(current)

    override suspend fun updateProfile(request: UpdateProfileRequest): DataResult<User> {
        updates += request
        updateFailure?.let { return DataResult.Failure(it) }
        current = current.copy(
            displayName = request.displayName ?: current.displayName,
            phoneNumber = request.phoneNumber ?: current.phoneNumber,
            defaultCurrency = request.defaultCurrency ?: current.defaultCurrency,
            avatarUrl = request.avatarUrl?.let { key -> key.ifEmpty { null }?.let { "https://cdn.test/$it" } } ?: current.avatarUrl,
        )
        return DataResult.Success(current)
    }

    override suspend fun search(query: String): DataResult<List<User>> = DataResult.Success(emptyList())
}

internal class FakeAccounts(private val users: FakeUsers = FakeUsers()) : AccountRepository {
    val emailRequests = mutableListOf<String>()
    var requestFailure: DataError? = null
    var validCode = "123456"
    var linked = LinkedAccountsResponse(emptyList(), hasPassword = true, availableProviders = OAuthProvider.entries)
    var linkFailure: DataError? = null
    val linkCalls = mutableListOf<Pair<OAuthProvider, String>>()
    var exportResult: DataResult<ByteArray> = DataResult.Success("""{"format":"splitfy-export/1"}""".encodeToByteArray())

    private var pending: String? = null

    override suspend fun requestEmailChange(newEmail: String): DataResult<EmailChangePending> {
        emailRequests += newEmail
        requestFailure?.let { return DataResult.Failure(it) }
        pending = newEmail.lowercase()
        return DataResult.Success(EmailChangePending(pending!!, epoch))
    }

    override suspend fun confirmEmailChange(code: String): DataResult<User> {
        val email = pending
        if (code != validCode || email == null) {
            return DataResult.Failure(DataError.Api(com.elozino.splitfy.api.ApiErrorCode.INVALID_CREDENTIALS, "Code is not valid or has expired"))
        }
        users.current = users.current.copy(email = email, emailVerified = true)
        return DataResult.Success(users.current)
    }

    override suspend fun linkedAccounts(): DataResult<LinkedAccountsResponse> = DataResult.Success(linked)

    override suspend fun link(provider: OAuthProvider, idToken: String, nonce: String?): DataResult<LinkedAccountsResponse> {
        linkCalls += provider to idToken
        linkFailure?.let { return DataResult.Failure(it) }
        linked = linked.copy(identities = linked.identities + LinkedIdentity(provider, "ada@gmail.com", epoch))
        return DataResult.Success(linked)
    }

    override suspend fun unlink(provider: OAuthProvider): DataResult<LinkedAccountsResponse> {
        linked = linked.copy(identities = linked.identities.filterNot { it.provider == provider })
        return DataResult.Success(linked)
    }

    override suspend fun exportData(): DataResult<ByteArray> = exportResult
}

internal class FakeCrashReporting(
    override val isAvailable: Boolean = true,
    override val appliesImmediately: Boolean = true,
) : CrashReportingSettings {
    override var isEnabled: Boolean = true
        private set

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}
