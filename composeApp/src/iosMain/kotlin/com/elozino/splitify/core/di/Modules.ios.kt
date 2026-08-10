package com.elozino.splitify.core.di

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Session tokens go in the Keychain, not `NSUserDefaults`: defaults are backed up
 * to iCloud and readable from a file-system dump of an unlocked device.
 */
@OptIn(ExperimentalSettingsImplementation::class)
actual fun platformModule(): Module = module {
    single<Settings> { KeychainSettings(service = "com.elozino.splitify.session") }
}
