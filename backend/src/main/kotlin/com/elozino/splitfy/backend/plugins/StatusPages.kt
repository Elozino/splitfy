package com.elozino.splitfy.backend.plugins

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.ApiErrorResponse
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.validation.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

/**
 * The single place an error becomes a response body.
 *
 * Expected failures ([AppException]) map to their declared status and code.
 * Anything unexpected is logged with its stack trace and reported as a generic 500 —
 * exception messages routinely contain SQL, table names or user data and must never
 * reach a client.
 */
fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("ErrorHandler")

    install(StatusPages) {

        exception<AppException> { call, cause ->
            if (cause.status.value >= HttpStatusCode.InternalServerError.value) {
                logger.error("Handled server error: {}", cause.message, cause)
            } else {
                logger.debug("Rejected request: {} {}", cause.code, cause.message)
            }
            call.respond(
                cause.status,
                ApiErrorResponse(
                    code = cause.code,
                    message = cause.message,
                    fieldErrors = cause.fieldErrors,
                    traceId = call.callId,
                ),
            )
        }

        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(
                    code = ApiErrorCode.VALIDATION_FAILED,
                    message = "The request failed validation",
                    fieldErrors = cause.fieldErrors,
                    traceId = call.callId,
                ),
            )
        }

        // Malformed or unparseable bodies: report the shape problem, not the parser's
        // internal message, which can echo raw request content back to the caller.
        exception<SerializationException> { call, cause ->
            logger.debug("Malformed request body", cause)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(
                    code = ApiErrorCode.MALFORMED_REQUEST,
                    message = "The request body is missing required fields or has the wrong shape",
                    traceId = call.callId,
                ),
            )
        }

        exception<BadRequestException> { call, cause ->
            logger.debug("Bad request", cause)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(
                    code = ApiErrorCode.MALFORMED_REQUEST,
                    message = "The request could not be understood",
                    traceId = call.callId,
                ),
            )
        }

        exception<NotFoundException> { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiErrorResponse(ApiErrorCode.NOT_FOUND, "Resource was not found", traceId = call.callId),
            )
        }

        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception on {}", call.request.local.uri, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse(
                    code = ApiErrorCode.INTERNAL_ERROR,
                    message = "Something went wrong on our end",
                    traceId = call.callId,
                ),
            )
        }

        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(
                status,
                ApiErrorResponse(
                    code = ApiErrorCode.RATE_LIMITED,
                    message = "Too many requests - slow down and try again shortly",
                    traceId = call.callId,
                ),
            )
        }
    }
}
