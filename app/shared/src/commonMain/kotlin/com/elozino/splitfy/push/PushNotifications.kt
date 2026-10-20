package com.elozino.splitfy.push

import androidx.compose.runtime.Composable
import com.elozino.splitfy.api.dto.DevicePlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the platforms hand push events to shared code.
 *
 * Android's `FirebaseMessagingService` and iOS's `AppDelegate` call in here (from
 * Swift as `PushNotifications.shared`); [PushRegistrar] and the navigation graph
 * observe the flows. Both values can arrive before Koin or the UI exist, which is
 * why this is a plain object rather than a DI binding.
 */
object PushNotifications {

    private val _token = MutableStateFlow<String?>(null)

    /** The device's current FCM registration token, once the platform has one. */
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _pendingGroupId = MutableStateFlow<String?>(null)

    /** A group whose notification was tapped and is waiting to be opened. */
    val pendingGroupId: StateFlow<String?> = _pendingGroupId.asStateFlow()

    fun onNewToken(token: String) {
        if (token.isNotBlank()) _token.value = token
    }

    /** Called with the `groupId` from a tapped notification's data payload. */
    fun openGroup(groupId: String?) {
        if (!groupId.isNullOrBlank()) _pendingGroupId.value = groupId
    }

    fun consumePendingGroup() {
        _pendingGroupId.value = null
    }
}

internal expect val pushPlatform: DevicePlatform

/**
 * Asks for permission to show notifications, once per launch, while someone is
 * signed in. Android 13+ shows the runtime prompt; iOS shows its prompt and then
 * registers for remote notifications.
 */
@Composable
expect fun RequestPushPermission()
