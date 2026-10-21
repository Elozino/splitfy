package com.elozino.splitfy.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

@Composable
actual fun rememberDeviceAuthenticator(): DeviceAuthenticator = remember { IosDeviceAuthenticator() }

internal actual val isApplePlatform: Boolean = true

/**
 * LocalAuthentication. Unlocking uses `deviceOwnerAuthentication`, which offers the
 * passcode after failed Face ID / Touch ID attempts.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosDeviceAuthenticator : DeviceAuthenticator {

    override fun availability(): DeviceAuthAvailability {
        val context = LAContext()
        if (context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) {
            val kind = when (context.biometryType) {
                LABiometryTypeFaceID -> BiometricKind.FACE
                LABiometryTypeTouchID -> BiometricKind.FINGERPRINT
                else -> BiometricKind.OTHER
            }
            return DeviceAuthAvailability.Biometric(kind)
        }
        return if (context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)) {
            DeviceAuthAvailability.DeviceCredentialOnly
        } else {
            DeviceAuthAvailability.Unavailable
        }
    }

    override suspend fun authenticate(reason: String): DeviceAuthResult = suspendCancellableCoroutine { continuation ->
        val context = LAContext()
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, error ->
            if (!continuation.isActive) return@evaluatePolicy
            val result = when {
                success -> DeviceAuthResult.Success
                error?.code in setOf(LAErrorUserCancel, LAErrorAppCancel, LAErrorSystemCancel) -> DeviceAuthResult.Cancelled
                else -> DeviceAuthResult.Failed(error?.localizedDescription ?: "Couldn't confirm it's you")
            }
            continuation.resume(result)
        }
        continuation.invokeOnCancellation { context.invalidate() }
    }
}
