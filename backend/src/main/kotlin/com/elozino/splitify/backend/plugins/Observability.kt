package com.elozino.splitify.backend.plugins

import com.elozino.splitify.api.ApiRoutes
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import java.util.UUID
import org.slf4j.event.Level

/**
 * Request correlation and access logging.
 *
 * Every request carries an id — either the caller's `X-Request-Id` or a generated
 * one — which is put into the MDC, echoed on error responses, and printed on every
 * log line, so a user-reported failure maps to exactly one request trace.
 */
fun Application.configureObservability() {

    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() && it.length <= MAX_CALL_ID_LENGTH }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc(MDC_TRACE_ID)
        // Health and readiness probes fire constantly and would drown the log.
        filter { call ->
            val path = call.request.path()
            path != ApiRoutes.System.HEALTH && path != ApiRoutes.System.READY
        }
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()?.value ?: "-"}"
        }
    }
}

const val MDC_TRACE_ID = "traceId"
private const val MAX_CALL_ID_LENGTH = 128
