package com.elozino.splitify

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

// Note: In a real KMP project, context management for non-composable functions 
// often requires a more robust setup. This is a simplified version for common use.
lateinit var appContext: android.content.Context

actual fun openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", appContext.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    appContext.startActivity(intent)
}
