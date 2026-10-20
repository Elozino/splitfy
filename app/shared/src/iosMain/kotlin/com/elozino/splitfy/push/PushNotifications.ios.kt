package com.elozino.splitfy.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.elozino.splitfy.api.dto.DevicePlatform
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal actual val pushPlatform: DevicePlatform = DevicePlatform.IOS

private var askedThisLaunch = false

/**
 * The APNs token arrives in `AppDelegate`, which hands it to Firebase Messaging; the
 * resulting FCM token comes back through [PushNotifications.onNewToken].
 */
@Composable
actual fun RequestPushPermission() {
    LaunchedEffect(Unit) {
        if (askedThisLaunch) return@LaunchedEffect
        askedThisLaunch = true
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            if (granted) {
                dispatch_async(dispatch_get_main_queue()) {
                    UIApplication.sharedApplication.registerForRemoteNotifications()
                }
            }
        }
    }
}
