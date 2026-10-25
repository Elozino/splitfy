import UIKit
import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ComposeHostController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Hosts the Compose UI.
///
/// On iPadOS 26 and later an app in a resizable window has its close, minimise and
/// full-screen controls drawn over the top-leading corner, inside the safe area. Compose
/// pads for the safe area only, so every screen's back arrow would sit under them. This
/// adds the extra room UIKit reports for that corner to the top of the Compose
/// controller's safe area, which moves top bars down below the controls. Full screen
/// the two insets match and nothing changes.
///
/// iPad only: on a phone in landscape the same corner region also steers clear of the
/// camera housing, which would push every top bar down for no window controls at all.
final class ComposeHostController: UIViewController {
    private let compose = MainViewControllerKt.MainViewController()

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard #available(iOS 26.0, *), traitCollection.userInterfaceIdiom == .pad else { return }
        let clearOfControls = view.edgeInsets(for: .safeArea(cornerAdaptation: .vertical)).top
        let extra = max(0, clearOfControls - view.safeAreaInsets.top)
        if compose.additionalSafeAreaInsets.top != extra {
            compose.additionalSafeAreaInsets.top = extra
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
