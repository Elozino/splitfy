package com.elozino.splitfy.crash

import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

/**
 * The Swift host starts Sentry at launch (see iOSApp.swift) and reads the same
 * [CRASH_REPORTS_ENABLED_KEY] from `UserDefaults` first, so a change applies from the
 * next launch.
 */
class IosCrashReportingSettings : CrashReportingSettings {
    private val defaults = NSUserDefaults.standardUserDefaults

    override val isAvailable: Boolean
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("SentryDsn") as? String)?.isNotBlank() == true

    override val isEnabled: Boolean
        get() = defaults.objectForKey(CRASH_REPORTS_ENABLED_KEY) == null || defaults.boolForKey(CRASH_REPORTS_ENABLED_KEY)

    override val appliesImmediately: Boolean = false

    override fun setEnabled(enabled: Boolean) = defaults.setBool(enabled, CRASH_REPORTS_ENABLED_KEY)
}
