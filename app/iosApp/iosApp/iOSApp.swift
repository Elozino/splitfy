import SwiftUI
import shared
#if canImport(Sentry)
import Sentry
#endif

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        startCrashReporting()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in InviteLinks.shared.open(url: url.absoluteString) }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL { InviteLinks.shared.open(url: url.absoluteString) }
                }
        }
    }
}

/// Starts Sentry when the build has a DSN (`SENTRY_DSN` in the xcconfig) and the
/// Sentry Swift package is linked; otherwise does nothing.
///
/// Sentry is started here rather than from Kotlin because the Kotlin side would
/// need Sentry's Cocoa framework at link time, including for the Kotlin/Native
/// test binaries CI builds. Crashes in shared Kotlin code are still caught: they
/// end the process the same way any native crash does.
///
/// Reports carry no email, IP address, screenshots, view hierarchy or network
/// details; see PiiScrubber.kt for the rules the Android side applies to text.
private func startCrashReporting() {
    #if canImport(Sentry)
    guard let dsn = Bundle.main.object(forInfoDictionaryKey: "SentryDsn") as? String,
          !dsn.trimmingCharacters(in: .whitespaces).isEmpty else { return }
    SentrySDK.start { options in
        options.dsn = dsn
        #if DEBUG
        options.environment = "debug"
        #else
        options.environment = "production"
        #endif
        options.sendDefaultPii = false
        options.attachScreenshot = false
        options.attachViewHierarchy = false
        options.enableNetworkBreadcrumbs = false
        options.enableCaptureFailedRequests = false
        options.enableNetworkTracking = false
        options.tracesSampleRate = 0
        options.beforeSend = { event in
            event.user = nil
            event.request = nil
            return event
        }
    }
    #endif
}
