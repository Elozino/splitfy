package com.elozino.splitfy

import androidx.compose.runtime.Composable
import platform.AVFoundation.*
import platform.Photos.*

actual fun checkPermission(type: PermissionType): PermissionStatus {
    return when (type) {
        PermissionType.CAMERA -> {
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
            when (status) {
                AVAuthorizationStatusAuthorized -> PermissionStatus.GRANTED
                AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> PermissionStatus.DENIED
                else -> PermissionStatus.DENIED
            }
        }
        PermissionType.GALLERY -> {
            val status = PHPhotoLibrary.authorizationStatus()
            when (status) {
                PHAuthorizationStatusAuthorized, PHAuthorizationStatusLimited -> PermissionStatus.GRANTED
                PHAuthorizationStatusDenied, PHAuthorizationStatusRestricted -> PermissionStatus.DENIED
                else -> PermissionStatus.DENIED
            }
        }
    }
}

@Composable
actual fun rememberPermissionLauncher(
    type: PermissionType,
    onResult: (PermissionStatus) -> Unit
): () -> Unit {
    return {
        when (type) {
            PermissionType.CAMERA -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    onResult(if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED)
                }
            }
            PermissionType.GALLERY -> {
                PHPhotoLibrary.requestAuthorization { status ->
                    onResult(
                        if (status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited) 
                            PermissionStatus.GRANTED 
                        else 
                            PermissionStatus.DENIED
                    )
                }
            }
        }
    }
}
