package com.elozino.splitify.android

import android.app.Application
import com.elozino.splitify.BuildConfig
import com.elozino.splitify.di.initKoin
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
        initKoin(isDebug = BuildConfig.DEBUG) {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@SplitfyApplication)
        }
    }
}
