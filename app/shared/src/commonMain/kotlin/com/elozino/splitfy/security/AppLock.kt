package com.elozino.splitfy.security

import androidx.compose.runtime.Composable
import com.elozino.splitfy.SessionManager
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which sensor the device unlocks with, for wording ("Face ID", "Fingerprint"). */
enum class BiometricKind { FACE, FINGERPRINT, OTHER }

sealed interface DeviceAuthAvailability {
    /** Biometrics are enrolled: App lock is offered. */
    data class Biometric(val kind: BiometricKind) : DeviceAuthAvailability

    /** Only a PIN, pattern or passcode: not offered, but an already-locked app can still be opened with it. */
    data object DeviceCredentialOnly : DeviceAuthAvailability

    data object Unavailable : DeviceAuthAvailability
}

sealed interface DeviceAuthResult {
    data object Success : DeviceAuthResult
    data object Cancelled : DeviceAuthResult
    data class Failed(val message: String) : DeviceAuthResult
}

/**
 * Face ID / Touch ID on iOS (LocalAuthentication), BiometricPrompt on Android. Both
 * fall back to the device passcode, so a failed face scan never locks anyone out.
 */
interface DeviceAuthenticator {
    fun availability(): DeviceAuthAvailability

    suspend fun authenticate(reason: String): DeviceAuthResult
}

/** Bound to the current screen: Android's prompt has to attach to the Activity. */
@Composable
expect fun rememberDeviceAuthenticator(): DeviceAuthenticator

/** User-facing name for the sensor. */
val BiometricKind.label: String
    get() = when (this) {
        BiometricKind.FACE -> if (isApplePlatform) "Face ID" else "Face unlock"
        BiometricKind.FINGERPRINT -> if (isApplePlatform) "Touch ID" else "Fingerprint"
        BiometricKind.OTHER -> "Biometrics"
    }

internal expect val isApplePlatform: Boolean

/**
 * App lock preferences. Local to this device on purpose: whether a phone needs a
 * face scan to open is about that phone, not the account.
 */
class AppLockSettings(private val settings: Settings) {

    private val _enabled = MutableStateFlow(settings.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _timeoutMinutes = MutableStateFlow(settings.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_MINUTES))
    /** How long the app may sit in the background before it locks; 0 locks on every return. */
    val timeoutMinutes: StateFlow<Int> = _timeoutMinutes.asStateFlow()

    fun setEnabled(value: Boolean) {
        settings[KEY_ENABLED] = value
        _enabled.value = value
    }

    fun setTimeoutMinutes(value: Int) {
        settings[KEY_TIMEOUT] = value
        _timeoutMinutes.value = value
    }

    companion object {
        val TIMEOUT_CHOICES = listOf(0, 1, 5, 15)
        const val DEFAULT_TIMEOUT_MINUTES = 1
        private const val KEY_ENABLED = "applock.enabled"
        private const val KEY_TIMEOUT = "applock.timeout_minutes"
    }
}

/**
 * Decides when the app is locked: at a cold start, and on returning from the
 * background after [AppLockSettings.timeoutMinutes]. Only while someone is signed
 * in; there is nothing to protect on the sign-in screens.
 *
 * Uses a monotonic clock, so changing the phone's time cannot skip the lock.
 */
class AppLockController(
    val settings: AppLockSettings,
    private val session: SessionManager,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private val _locked = MutableStateFlow(settings.enabled.value && session.isSignedIn)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: TimeMark? = null

    fun onBackground() {
        backgroundedAt = timeSource.markNow()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (!settings.enabled.value || !session.isSignedIn) return
        if (since.elapsedNow() >= settings.timeoutMinutes.value.minutes) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    /** Turning the lock off (or signing out) must never leave the app stuck behind it. */
    fun setEnabled(value: Boolean) {
        settings.setEnabled(value)
        if (!value) _locked.value = false
    }
}
