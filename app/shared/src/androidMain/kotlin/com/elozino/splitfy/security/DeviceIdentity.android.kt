package com.elozino.splitfy.security

import android.os.Build
import android.provider.Settings
import com.elozino.splitfy.api.dto.DevicePlatform
import com.elozino.splitfy.applicationContext

/** The name the user gave the phone in Settings when there is one, else maker and model. */
actual fun currentDeviceIdentity(): DeviceIdentity {
    val userName = runCatching {
        Settings.Global.getString(applicationContext.contentResolver, "device_name")
    }.getOrNull()?.takeIf { it.isNotBlank() }
    val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
        Build.MODEL
    } else {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }
    return DeviceIdentity(name = userName ?: model, platform = DevicePlatform.ANDROID)
}
