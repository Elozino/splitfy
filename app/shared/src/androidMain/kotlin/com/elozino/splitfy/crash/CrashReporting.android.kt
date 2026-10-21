package com.elozino.splitfy.crash

import io.sentry.SentryOptions
import io.sentry.kotlin.multiplatform.Sentry

/**
 * Crash reporting for the Android app, through Sentry's Kotlin Multiplatform SDK.
 *
 * Off unless a DSN was configured at build time (`sentry.dsn`). Reports carry the
 * stack trace, device and app version — no email, no IP address, no screenshots,
 * no view hierarchy, no session replay and no HTTP request data. Whatever free
 * text is left (exception messages, breadcrumbs) goes through [PiiScrubber].
 *
 * Configured through the platform options rather than the common ones because only
 * the Android event model lets exception messages be rewritten.
 */
object CrashReporting {

    fun start(dsn: String, isDebug: Boolean, release: String) {
        if (dsn.isBlank()) return
        Sentry.initWithPlatformOptions { options ->
            options.dsn = dsn
            options.environment = if (isDebug) "debug" else "production"
            options.release = release
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.sessionReplay.sessionSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0
            options.tracesSampleRate = null
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                breadcrumb.message = breadcrumb.message?.let(PiiScrubber::scrub)
                breadcrumb.data.clear()
                breadcrumb
            }
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                event.user = null
                event.request = null
                event.message?.let { message ->
                    message.message = message.message?.let(PiiScrubber::scrub)
                    message.formatted = message.formatted?.let(PiiScrubber::scrub)
                    message.params = null
                }
                event.exceptions?.forEach { it.value = it.value?.let(PiiScrubber::scrub) }
                event
            }
        }
    }
}
