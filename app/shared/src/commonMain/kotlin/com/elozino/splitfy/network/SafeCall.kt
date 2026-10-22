package com.elozino.splitfy.network

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.ApiErrorResponse
import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * The single boundary where transport failures become [DataError]s.
 *
 * Every repository call goes through this, so no exception type from Ktor or
 * kotlinx.serialization ever escapes the data layer.
 */
class ApiCaller(private val sessionManager: SessionManager) {

    suspend inline fun <reified T> execute(
        crossinline request: suspend () -> HttpResponse,
    ): DataResult<T> = try {
        val response = request()
        if (response.status.isSuccess()) {
            DataResult.Success(response.body<T>())
        } else {
            DataResult.Failure(response.toDataError())
        }
    } catch (cancellation: CancellationException) {
        // Never swallow cancellation: it would keep a dead screen's spinner running.
        throw cancellation
    } catch (refresh: RefreshUnavailableException) {
        DataError.Api(ApiErrorCode.INTERNAL_ERROR, "We couldn't reach Splitfy right now. Please try again.")
            .let { DataResult.Failure(it) }
    } catch (timeout: SocketTimeoutException) {
        DataResult.Failure(DataError.Timeout)
    } catch (timeout: ConnectTimeoutException) {
        DataResult.Failure(DataError.Timeout)
    } catch (io: IOException) {
        DataResult.Failure(DataError.Offline)
    } catch (serialization: SerializationException) {
        DataResult.Failure(DataError.Unknown("Unexpected response from the server"))
    } catch (throwable: Throwable) {
        DataResult.Failure(DataError.Unknown(throwable.message ?: "Unexpected error"))
    }

    suspend fun HttpResponse.toDataError(): DataError {
        val parsed = runCatching {
            HttpClientFactory.json.decodeFromString<ApiErrorResponse>(bodyAsText())
        }.getOrNull()

        return when (status) {
            // A wrong password, OTP or reset token is a 401 too, but it says nothing
            // about the session: report it so the form can show it, and stay signed in.
            HttpStatusCode.Unauthorized if parsed?.code == ApiErrorCode.INVALID_CREDENTIALS ->
                DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, parsed.message, parsed.traceId)
            HttpStatusCode.Unauthorized -> {
                // Ktor's Bearer plugin already tried to refresh; reaching here means
                // the session is genuinely dead.
                sessionManager.signOut()
                DataError.Unauthenticated
            }
            HttpStatusCode.Forbidden ->
                DataError.Forbidden(parsed?.message ?: "You don't have access to this")
            HttpStatusCode.NotFound ->
                DataError.NotFound(parsed?.message ?: "Not found")
            HttpStatusCode.BadRequest ->
                if (parsed != null && parsed.fieldErrors.isNotEmpty()) {
                    DataError.Validation(parsed.fieldErrors, parsed.message)
                } else {
                    DataError.Api(
                        parsed?.code ?: ApiErrorCode.MALFORMED_REQUEST,
                        parsed?.message ?: "That request wasn't valid",
                        parsed?.traceId,
                    )
                }
            HttpStatusCode.TooManyRequests ->
                DataError.RateLimited(parsed?.message ?: "Too many attempts. Try again shortly.")
            else -> DataError.Api(
                code = parsed?.code ?: ApiErrorCode.INTERNAL_ERROR,
                message = parsed?.message ?: "Something went wrong on our end",
                traceId = parsed?.traceId,
            )
        }
    }
}
