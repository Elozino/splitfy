package com.elozino.splitify.core.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Android context comes from Koin rather than a global `lateinit var`, so
 * nothing can read it before the graph is initialised.
 */
actual fun platformModule(): Module = module {
    single<Settings> {
        val context: Context = androidContext()
        SharedPreferencesSettings(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    }
}

private const val PREFERENCES_NAME = "splitfy.session"
