package com.elozino.splitfy.backend.plugins

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.backend.utils.AppConfig
import com.elozino.splitfy.backend.utils.constantTimeEquals
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.UUID
import org.slf4j.event.Level

/**
 * Request correlation, access logging and metrics.
 *
 * Every request carries an id — either the caller's `X-Request-Id` or a generated
 * one — which is put into the MDC, echoed on error responses, and printed on every
 * log line, so a user-reported failure maps to exactly one request trace.
 *
 * The access log holds method, path, status and duration only. Query strings are
 * never logged (search takes an email address there) and secrets that travel in
 * the path — invite tokens — are masked.
 */
fun Application.configureObservability(): PrometheusMeterRegistry {

    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() && it.length <= MAX_CALL_ID_LENGTH }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc(MDC_TRACE_ID)
        // Health, readiness and scrape traffic fire constantly and would drown the log.
        filter { call -> call.request.path() !in QUIET_PATHS }
        format { call ->
            "${call.request.httpMethod.value} ${call.redactedPath()} -> " +
                "${call.response.status()?.value ?: "-"} in ${call.processingTimeMillis()}ms"
        }
    }

    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        this.registry = registry
        // Tag by route template, not the raw path, so ids and tokens never become labels.
        distinctNotRegisteredRoutes = false
    }
    return registry
}

/**
 * Prometheus scrape endpoint. Answers 404 unless `METRICS_TOKEN` is configured, and
 * then only to `Authorization: Bearer <token>` — so it can sit on the public port of
 * a platform that exposes just one, without publishing anything.
 */
fun Route.metricsRoute(registry: PrometheusMeterRegistry, config: AppConfig.ObservabilityConfig) {
    get(ApiRoutes.System.METRICS) {
        val token = config.metricsToken
        val presented = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        when {
            token.isEmpty() -> call.respondText("Not Found", status = HttpStatusCode.NotFound)
            presented == null || !constantTimeEquals(presented, token) ->
                call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
            else -> call.respondText(registry.scrape(), ContentType.parse("text/plain; version=0.0.4"))
        }
    }
}

/** The request path with path-borne secrets masked; safe for logs and error reports. */
fun ApplicationCall.redactedPath(): String = redactPath(request.path())

internal fun redactPath(path: String): String =
    SECRET_SEGMENTS.fold(path) { current, pattern -> pattern.replace(current) { "${it.groupValues[1]}***" } }

const val MDC_TRACE_ID = "traceId"
private const val MAX_CALL_ID_LENGTH = 128
private val QUIET_PATHS = setOf(ApiRoutes.System.HEALTH, ApiRoutes.System.READY, ApiRoutes.System.METRICS)

/** Invite tokens are bearer secrets: `/i/<token>` and `/api/v1/invites/<token>[/accept]`. */
private val SECRET_SEGMENTS = listOf(
    Regex("""^(/i/)[^/]+"""),
    Regex("""^(${Regex.escape(ApiRoutes.Invites.BASE)}/)[^/]+"""),
)
