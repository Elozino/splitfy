package com.elozino.splitfy.repositories

import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.EmailChangeConfirmRequest
import com.elozino.splitfy.api.dto.EmailChangePending
import com.elozino.splitfy.api.dto.EmailChangeRequest
import com.elozino.splitfy.api.dto.LinkedAccountsResponse
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.network.ApiCaller
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/** The `/users/me/...` account features: email change, linked sign-in methods, data export. */
interface AccountRepository {
    suspend fun requestEmailChange(newEmail: String): DataResult<EmailChangePending>
    suspend fun confirmEmailChange(code: String): DataResult<User>
    suspend fun linkedAccounts(): DataResult<LinkedAccountsResponse>
    suspend fun link(provider: OAuthProvider, idToken: String, nonce: String?): DataResult<LinkedAccountsResponse>
    suspend fun unlink(provider: OAuthProvider): DataResult<LinkedAccountsResponse>

    /** The export file exactly as the server sent it (JSON). */
    suspend fun exportData(): DataResult<ByteArray>
}

class AccountRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : AccountRepository {

    override suspend fun requestEmailChange(newEmail: String): DataResult<EmailChangePending> =
        caller.execute { client.post(ApiRoutes.Users.EMAIL_CHANGE) { setBody(EmailChangeRequest(newEmail)) } }

    override suspend fun confirmEmailChange(code: String): DataResult<User> =
        caller.execute { client.post(ApiRoutes.Users.EMAIL_CHANGE_CONFIRM) { setBody(EmailChangeConfirmRequest(code)) } }

    override suspend fun linkedAccounts(): DataResult<LinkedAccountsResponse> =
        caller.execute { client.get(ApiRoutes.Users.IDENTITIES) }

    override suspend fun link(provider: OAuthProvider, idToken: String, nonce: String?): DataResult<LinkedAccountsResponse> =
        caller.execute {
            client.post(ApiRoutes.Users.identity(provider.path)) { setBody(OAuthSignInRequest(idToken, nonce)) }
        }

    override suspend fun unlink(provider: OAuthProvider): DataResult<LinkedAccountsResponse> =
        caller.execute { client.delete(ApiRoutes.Users.identity(provider.path)) }

    // A ByteArray body skips JSON decoding, so the file is saved byte for byte.
    override suspend fun exportData(): DataResult<ByteArray> =
        caller.execute { client.get(ApiRoutes.Users.EXPORT) }
}
