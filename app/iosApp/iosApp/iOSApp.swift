import SwiftUI
import shared

@main
struct iOSApp: App {
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