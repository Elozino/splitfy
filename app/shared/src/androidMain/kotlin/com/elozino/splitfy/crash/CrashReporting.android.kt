package com.elozino.splitfy.crash

import android.content.Context
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
 *
 * The user can turn it off under Data & Analytics; that choice is stored on the
 * device and checked before Sentry starts.
 */
object CrashReporting {

    private const val PREFERENCES_NAME = "splitfy.crash"

    private var config: Config? = null

    private data class Config(val dsn: String, val isDebug: Boolean, val release: String)

    fun start(context: Context, dsn: String, isDebug: Boolean, release: String) {
        if (dsn.isBlank()) return
        config = Config(dsn, isDebug, release)
        if (isEnabled(context)) init(config!!)
    }

    internal val isConfigured: Boolean get() = config != null

    internal fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(CRASH_REPORTS_ENABLED_KEY, true)

    /** Stops or starts reporting straight away, and remembers the choice. */
    internal fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(CRASH_REPORTS_ENABLED_KEY, enabled).apply()
        val config = config ?: return
        if (enabled) init(config) else Sentry.close()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun init(config: Config) {
        val (dsn, isDebug, release) = config
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

class AndroidCrashReportingSettings(private val context: Context) : CrashReportingSettings {
    override val isAvailable: Boolean get() = CrashReporting.isConfigured
    override val isEnabled: Boolean get() = CrashReporting.isEnabled(context)
    override val appliesImmediately: Boolean = true
    override fun setEnabled(enabled: Boolean) = CrashReporting.setEnabled(context, enabled)
}
