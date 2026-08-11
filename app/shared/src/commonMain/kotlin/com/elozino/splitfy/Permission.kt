package com.elozino.splitfy

import androidx.compose.runtime.Composable

enum class PermissionType {
    CAMERA,
    GALLERY
}

enum class PermissionStatus {
    GRANTED,
    DENIED,
    SHOW_RATIONALE
}

expect fun checkPermission(type: PermissionType): PermissionStatus

@Composable
expect fun rememberPermissionLauncher(
    type: PermissionType,
    onResult: (PermissionStatus) -> Unit
): () -> Unit
