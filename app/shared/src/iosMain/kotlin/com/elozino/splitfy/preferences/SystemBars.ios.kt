package com.elozino.splitfy.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

@Composable
actual fun SystemBarsEffect(darkTheme: Boolean) {
    SideEffect {
        // Overriding the window's style flips the status bar and any system sheets
        // (share sheet, Sign in with Apple) to match the app.
        UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().forEach { window ->
            window.overrideUserInterfaceStyle = if (darkTheme) {
                UIUserInterfaceStyle.UIUserInterfaceStyleDark
            } else {
                UIUserInterfaceStyle.UIUserInterfaceStyleLight
            }
        }
    }
}
