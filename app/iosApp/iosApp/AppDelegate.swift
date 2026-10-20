import UIKit
import UserNotifications
import shared
#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
import FirebaseCore
import FirebaseMessaging
#endif

/// Push notifications.
///
/// The server sends through FCM, so iOS needs the Firebase Messaging SDK to turn the
/// APNs device token into an FCM registration token. It is optional: until the
/// FirebaseMessaging Swift package and GoogleService-Info.plist are added (see
/// docs/PUSH_NOTIFICATIONS.md) the app builds and runs, it just never registers.
/// The permission prompt itself is asked from shared code after sign-in
/// (RequestPushPermission in PushNotifications.ios.kt).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        #if canImport(FirebaseCore) && canImport(FirebaseMessaging)
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
        }
        #endif
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        #if canImport(FirebaseCore) && canImport(FirebaseMessaging)
        if FirebaseApp.app() != nil {
            Messaging.messaging().apnsToken = deviceToken
        }
        #endif
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Remote notifications unavailable: \(error.localizedDescription)")
    }

    /// Show banners while the app is open too; the in-app list updates separately.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    /// A tap opens the group the notification is about.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let groupId = response.notification.request.content.userInfo[PushData.shared.GROUP_ID] as? String
        PushNotifications.shared.openGroup(groupId: groupId)
        completionHandler()
    }
}

#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        if let fcmToken { PushNotifications.shared.onNewToken(token: fcmToken) }
    }
}
#endif
