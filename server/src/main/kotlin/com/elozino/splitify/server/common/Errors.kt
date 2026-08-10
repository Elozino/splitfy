package com.elozino.splitify.server.common

import com.elozino.splitify.shared.api.ApiErrorCode
import com.elozino.splitify.shared.api.FieldError
import io.ktor.http.HttpStatusCode

/**
 * Every failure the API deliberately produces is one of these.
 *
 * Routes and services throw; a single `StatusPages` handler turns them into the
 * shared `ApiErrorResponse` envelope. Nothing else in the codebase builds an error
 * body by hand, so the wire format cannot drift between endpoints.
 */
sealed class AppException(
    val status: HttpStatusCode,
    val code: ApiErrorCode,
    override val message: String,
    val fieldErrors: List<FieldError> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class BadRequest(
        message: String,
        code: ApiErrorCode = ApiErrorCode.MALFORMED_REQUEST,
        fieldErrors: List<FieldError> = emptyList(),
    ) : AppException(HttpStatusCode.BadRequest, code, message, fieldErrors)

    class Validation(
        fieldErrors: List<FieldError>,
        message: String = "The request failed validation",
    ) : AppException(HttpStatusCode.BadRequest, ApiErrorCode.VALIDATION_FAILED, message, fieldErrors)

    class Unauthorized(
        message: String = "Authentication is required",
        code: ApiErrorCode = ApiErrorCode.UNAUTHORIZED,
    ) : AppException(HttpStatusCode.Unauthorized, code, message)

    class InvalidCredentials(
        message: String = "Email or password is incorrect",
    ) : AppException(HttpStatusCode.Unauthorized, ApiErrorCode.INVALID_CREDENTIALS, message)

    class Forbidden(
        message: String = "You do not have access to this resource",
    ) : AppException(HttpStatusCode.Forbidden, ApiErrorCode.FORBIDDEN, message)

    class NotFound(
        resource: String,
    ) : AppException(HttpStatusCode.NotFound, ApiErrorCode.NOT_FOUND, "$resource was not found")

    class Conflict(
        message: String,
        code: ApiErrorCode = ApiErrorCode.CONFLICT,
    ) : AppException(HttpStatusCode.Conflict, code, message)

    class Internal(
        message: String = "Something went wrong on our end",
        cause: Throwable? = null,
    ) : AppException(HttpStatusCode.InternalServerError, ApiErrorCode.INTERNAL_ERROR, message, cause = cause)
}
