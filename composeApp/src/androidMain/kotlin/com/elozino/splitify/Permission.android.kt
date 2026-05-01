package com.elozino.splitify

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity

actual fun checkPermission(type: PermissionType): PermissionStatus {
    val permission = when (type) {
        PermissionType.CAMERA -> Manifest.permission.CAMERA
        PermissionType.GALLERY -> Manifest.permission.READ_EXTERNAL_STORAGE // Simplification for demo
    }
    
    val context = appContext
    return when {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
            PermissionStatus.GRANTED
        }
        else -> PermissionStatus.DENIED
    }
}

@Composable
actual fun rememberPermissionLauncher(
    type: PermissionType,
    onResult: (PermissionStatus) -> Unit
): () -> Unit {
    val permission = when (type) {
        PermissionType.CAMERA -> Manifest.permission.CAMERA
        PermissionType.GALLERY -> Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onResult(if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED)
    }

    return {
        launcher.launch(permission)
    }
}
