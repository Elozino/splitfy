package com.elozino.splitfy

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.FieldError

/**
 * Failures the UI can actually react to.
 *
 * Exceptions are caught at the network boundary and turned into one of these, so a
 * ViewModel never has to know about `IOException` or HTTP status codes.
 */
sealed interface DataError {

    /** No usable connection. The UI should offer a retry, not an error dialog. */
    data object Offline : DataError

    data object Timeout : DataError

    /** The session is gone; the app must return to the login screen. */
    data object Unauthenticated : DataError

    data class Forbidden(val message: String) : DataError

    data class NotFound(val message: String) : DataError

    data class Validation(val fieldErrors: List<FieldError>, val message: String) : DataError

    data class RateLimited(val message: String) : DataError

    /** Anything the server reported with a code the client understands. */
    data class Api(val code: ApiErrorCode, val message: String, val traceId: String? = null) : DataError

    /** Everything else, including serialization failures. */
    data class Unknown(val message: String) : DataError
}

/** A deliberately small Either, so call sites must handle the failure branch. */
sealed interface DataResult<out T> {

    data class Success<T>(val data: T) : DataResult<T>

    data class Failure(val error: DataError) : DataResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorOrNull(): DataError? = (this as? Failure)?.error
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Failure -> this
}

inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> = apply {
    if (this is DataResult.Success) action(data)
}

inline fun <T> DataResult<T>.onFailure(action: (DataError) -> Unit): DataResult<T> = apply {
    if (this is DataResult.Failure) action(error)
}

/** Human-readable copy for a failure. Kept here so every screen words it the same. */
fun DataError.userMessage(): String = when (this) {
    DataError.Offline -> "You're offline. Check your connection and try again."
    DataError.Timeout -> "That took too long. Try again."
    DataError.Unauthenticated -> "Your session expired. Please sign in again."
    is DataError.Forbidden -> message
    is DataError.NotFound -> message
    is DataError.Validation -> fieldErrors.firstOrNull()?.message ?: message
    is DataError.RateLimited -> message
    is DataError.Api -> message
    is DataError.Unknown -> "Something went wrong. Please try again."
}
