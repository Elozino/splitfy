package com.elozino.splitify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.koin.core.context.GlobalContext

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

/**
 * The application context comes from the DI graph rather than a global `lateinit
 * var` set by the Activity: nothing can read it before Koin has started, and it
 * survives Activity recreation.
 */
internal val applicationContext: Context
    get() = GlobalContext.get().get()

actual fun openAppSettings() {
    val context = applicationContext
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
