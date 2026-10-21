package com.elozino.splitfy.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.elozino.splitfy.api.dto.DevicePlatform

internal actual val pushPlatform: DevicePlatform = DevicePlatform.ANDROID

/** Only asked once per process: Android stops showing the prompt after two refusals anyway. */
private var askedThisLaunch = false

@Composable
actual fun RequestPushPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !askedThisLaunch) {
            askedThisLaunch = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
