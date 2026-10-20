package com.elozino.splitfy.backend.plugins

import com.elozino.splitfy.backend.utils.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions

/**
 * Crash and error reporting through Sentry, off unless `SENTRY_DSN` is set.
 *
 * Every Sentry call is a no-op until [Sentry.init] runs, so callers never check
 * whether reporting is enabled. Events carry the exception, the request's trace id
 * and the build — never the caller's email, IP address, headers, body or tokens.
 */
fun Application.configureErrorReporting(config: AppConfig) {
    val observability = config.observability
    if (observability.sentryDsn.isBlank()) {
        log.info("Sentry DSN not set; error reporting is off")
        return
    }

    Sentry.init { options ->
        options.dsn = observability.sentryDsn
        options.environment = config.environment.name.lowercase()
        observability.release.takeIf { it.isNotEmpty() }?.let { options.release = it }
        options.isSendDefaultPii = false
        // Errors only: performance tracing would ship request paths and timings we
        // have not reviewed for personal data.
        options.tracesSampleRate = null
        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> PiiScrubber.scrub(event) }
    }
    log.info("Sentry error reporting is on for {}", config.environment)

    monitor.subscribe(ApplicationStopped) { Sentry.close() }
}

/** Reports an unexpected failure, tagged with the request's trace id so it maps to one log line. */
fun reportError(cause: Throwable, traceId: String?) {
    Sentry.withScope { scope ->
        traceId?.let { scope.setTag(MDC_TRACE_ID, it) }
        Sentry.captureException(cause)
    }
}

/**
 * Last line of defence before an event leaves the process. Exception messages from
 * the database driver routinely quote the offending row (`Key (email)=(...)`), so
 * free text is scrubbed as well as the structured fields.
 */
internal object PiiScrubber {

    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val bearer = Regex("""(?i)bearer\s+[A-Za-z0-9._~+/=-]+""")
    private val jwt = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*""")

    fun scrubText(text: String): String =
        text.replace(jwt, "[jwt]").replace(bearer, "Bearer [token]").replace(email, "[email]")

    fun scrub(event: SentryEvent): SentryEvent {
        event.user?.let { user ->
            user.email = null
            user.ipAddress = null
            user.username = null
            user.name = null
        }
        event.request = null
        event.message?.let { message ->
            message.formatted = message.formatted?.let(::scrubText)
            message.message = message.message?.let(::scrubText)
            message.params = null
        }
        event.exceptions?.forEach { exception -> exception.value = exception.value?.let(::scrubText) }
        event.breadcrumbs?.forEach { crumb -> crumb.message = crumb.message?.let(::scrubText) }
        return event
    }
}
