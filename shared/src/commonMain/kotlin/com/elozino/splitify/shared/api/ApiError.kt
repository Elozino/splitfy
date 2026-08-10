package com.elozino.splitify.shared.api

import kotlinx.serialization.Serializable

/**
 * Machine-readable error codes. Clients branch on these, never on message strings,
 * so error copy can be reworded without breaking behaviour.
 */
@Serializable
enum class ApiErrorCode {
    // 400
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    SPLIT_INVALID,
    CURRENCY_MISMATCH,

    // 401 / 403
    INVALID_CREDENTIALS,
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    UNAUTHORIZED,
    FORBIDDEN,

    // 404 / 409
    NOT_FOUND,
    EMAIL_ALREADY_REGISTERED,
    ALREADY_MEMBER,
    CONFLICT,
    INSUFFICIENT_FUNDS,
    PAYMENT_METHOD_REQUIRED,

    // 429 / 5xx
    RATE_LIMITED,
    INTERNAL_ERROR,
    SERVICE_UNAVAILABLE,
}

/** Field-level detail attached to a [ApiErrorCode.VALIDATION_FAILED] response. */
@Serializable
data class FieldError(
    val field: String,
    val message: String,
)

/**
 * The one and only error envelope the API ever returns.
 *
 * [traceId] is echoed from the request's correlation id so a user-reported failure
 * can be found in the logs without guesswork.
 */
@Serializable
data class ApiErrorResponse(
    val code: ApiErrorCode,
    val message: String,
    val fieldErrors: List<FieldError> = emptyList(),
    val traceId: String? = null,
)
