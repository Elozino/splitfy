package com.elozino.splitfy.android

import android.app.Application
import com.elozino.splitfy.BuildConfig
import com.elozino.splitfy.android.push.SplitfyMessagingService
import com.elozino.splitfy.crash.CrashReporting
import com.elozino.splitfy.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

/**
 * Starts the dependency graph before any Activity exists, so nothing has to guard
 * against Koin not being ready yet.
 */
class SplitfyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // First, so a crash while wiring up the app is still reported.
        CrashReporting.start(
            dsn = BuildConfig.SENTRY_DSN,
            isDebug = BuildConfig.DEBUG,
            release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
        )
        initKoin(
            isDebug = BuildConfig.DEBUG,
            apiBaseUrl = BuildConfig.API_BASE_URL,
            googleClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        ) {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@SplitfyApplication)
        }
        SplitfyMessagingService.initialise(this)
    }
}
