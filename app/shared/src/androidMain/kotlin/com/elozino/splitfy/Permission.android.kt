package com.elozino.splitfy

import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

/**
 * Gallery access changed in API 33: `READ_EXTERNAL_STORAGE` was replaced by the
 * per-type `READ_MEDIA_IMAGES`, and asking for the old one on a newer device is
 * denied outright.
 */
private fun PermissionType.androidPermission(): String = when (this) {
    PermissionType.CAMERA -> Manifest.permission.CAMERA
    PermissionType.GALLERY ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}

actual fun checkPermission(type: PermissionType): PermissionStatus =
    if (ContextCompat.checkSelfPermission(applicationContext, type.androidPermission()) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        PermissionStatus.GRANTED
    } else {
        PermissionStatus.DENIED
    }

@Composable
actual fun rememberPermissionLauncher(
    type: PermissionType,
    onResult: (PermissionStatus) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onResult(if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED)
    }
    return { launcher.launch(type.androidPermission()) }
}
