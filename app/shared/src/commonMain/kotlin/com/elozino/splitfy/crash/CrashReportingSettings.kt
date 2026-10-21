package com.elozino.splitfy.crash

/**
 * The per-device "Send crash reports" switch. On by default; turning it off is
 * remembered on this device only, since crash reporting starts before anyone signs in.
 */
interface CrashReportingSettings {
    /** False when this build has no crash reporting configured, so there is nothing to switch. */
    val isAvailable: Boolean

    val isEnabled: Boolean

    /** Whether [setEnabled] takes effect now, or only the next time the app starts. */
    val appliesImmediately: Boolean

    fun setEnabled(enabled: Boolean)
}

/** Preference key shared with the iOS host app, which starts Sentry itself. */
const val CRASH_REPORTS_ENABLED_KEY = "splitfy.crashReports.enabled"
