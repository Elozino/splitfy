package com.elozino.splitfy.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
actual fun rememberDeviceAuthenticator(): DeviceAuthenticator {
    val context = LocalContext.current
    return remember(context) { AndroidDeviceAuthenticator(context) }
}

internal actual val isApplePlatform: Boolean = false

/**
 * BiometricPrompt with the screen lock as fallback (BIOMETRIC_WEAK | DEVICE_CREDENTIAL
 * is the one combination androidx supports on every API level we run on).
 */
private class AndroidDeviceAuthenticator(private val context: Context) : DeviceAuthenticator {

    override fun availability(): DeviceAuthAvailability {
        val manager = BiometricManager.from(context)
        if (manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            return DeviceAuthAvailability.Biometric(sensorKind())
        }
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return if (keyguard?.isDeviceSecure == true) {
            DeviceAuthAvailability.DeviceCredentialOnly
        } else {
            DeviceAuthAvailability.Unavailable
        }
    }

    override suspend fun authenticate(reason: String): DeviceAuthResult {
        val activity = context.findFragmentActivity()
            ?: return DeviceAuthResult.Failed("Couldn't show the unlock prompt")

        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(DeviceAuthResult.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!continuation.isActive) return
                        val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        continuation.resume(if (cancelled) DeviceAuthResult.Cancelled else DeviceAuthResult.Failed(errString.toString()))
                    }

                    // A face or finger that didn't match: the prompt stays up for another try.
                    override fun onAuthenticationFailed() = Unit
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Splitfy")
                    .setSubtitle(reason)
                    .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build(),
            )
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private fun sensorKind(): BiometricKind {
        val packages = context.packageManager
        val face = packages.hasSystemFeature(PackageManager.FEATURE_FACE)
        val fingerprint = packages.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        return when {
            fingerprint && !face -> BiometricKind.FINGERPRINT
            face && !fingerprint -> BiometricKind.FACE
            else -> BiometricKind.OTHER
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
