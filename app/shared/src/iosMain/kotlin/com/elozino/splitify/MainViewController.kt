package com.elozino.splitify

import androidx.compose.ui.window.ComposeUIViewController
import com.elozino.splitify.di.initKoin
import kotlin.experimental.ExperimentalNativeApi

private var koinStarted = false

/**
 * Called from Swift (`ComposeView` in `iosApp`).
 *
 * Koin is started here rather than in Swift so both platforms share one
 * initialisation path; the guard keeps it a no-op if SwiftUI recreates the view.
 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController {
    if (!koinStarted) {
        koinStarted = true
        // Fully qualified: this module also declares its own `Platform` interface.
        initKoin(isDebug = kotlin.native.Platform.isDebugBinary)
    }
    App()
}
