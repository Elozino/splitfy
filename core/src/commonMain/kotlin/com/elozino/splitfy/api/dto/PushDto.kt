package com.elozino.splitfy.api.dto

import kotlinx.serialization.Serializable

enum class DevicePlatform { ANDROID, IOS }

/** Registers (or re-assigns) an FCM registration token to the caller. Idempotent. */
@Serializable
data class RegisterDeviceRequest(
    val token: String,
    val platform: DevicePlatform,
)

@Serializable
data class UnregisterDeviceRequest(
    val token: String,
)

/**
 * Which events reach the user's phone as a push notification.
 *
 * In-app notifications are always kept; these only gate the push. Security and
 * account messages are not optional and have no switch.
 */
@Serializable
data class NotificationPreferences(
    val newExpenses: Boolean = true,
    val settlements: Boolean = true,
    val groupActivity: Boolean = true,
    val paymentReminders: Boolean = true,
)

/** A partial update: null leaves that switch as it is. */
@Serializable
data class UpdateNotificationPreferencesRequest(
    val newExpenses: Boolean? = null,
    val settlements: Boolean? = null,
    val groupActivity: Boolean? = null,
    val paymentReminders: Boolean? = null,
)

/** Keys in a push message's data payload, read by the apps when a notification is tapped. */
object PushData {
    const val GROUP_ID = "groupId"
    const val TYPE = "type"
}
