package com.elozino.splitfy.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.setStatusBarStyle

@Composable
actual fun SystemBarsEffect(darkTheme: Boolean, darkStatusBarIcons: Boolean) {
    SideEffect {
        // Overriding the window's style flips system sheets (share sheet, Sign in with
        // Apple) and the keyboard to match the app.
        UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().forEach { window ->
            window.overrideUserInterfaceStyle = if (darkTheme) {
                UIUserInterfaceStyle.UIUserInterfaceStyleDark
            } else {
                UIUserInterfaceStyle.UIUserInterfaceStyleLight
            }
        }
        // The status bar is set app-wide (UIViewControllerBasedStatusBarAppearance is
        // NO in Info.plist): the SwiftUI host controller ignores the Compose
        // controller's preferredStatusBarStyle, so a per-controller style can't reach it.
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.setStatusBarStyle(
            if (darkStatusBarIcons) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent,
            animated = true,
        )
    }
}
