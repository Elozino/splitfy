package com.elozino.splitfy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.koinInject
import com.elozino.splitfy.ui.theme.isAppInDarkTheme
import com.elozino.splitfy.preferences.SystemBarsEffect
import com.elozino.splitfy.preferences.ThemePreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.setSingletonImageLoaderFactory
import coil3.ImageLoader
import coil3.svg.SvgDecoder
import com.elozino.splitfy.navigation.NavSetup
import com.elozino.splitfy.security.AppLockGate
import com.elozino.splitfy.ui.theme.AppTheme
import org.koin.compose.KoinContext

/**
 * The shared UI entry point for both platforms.
 *
 * Koin is started by each platform's entry point (Android's `Application`, iOS's
 * `MainViewController`) before this runs; [KoinContext] just exposes that graph to
 * `koinViewModel()` call sites further down the tree.
 */
@Composable
@Preview
fun App() {
    KoinContext {
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(SvgDecoder.Factory()) }
                .build()
        }

        val themeMode by koinInject<ThemePreferences>().mode.collectAsState()
        AppTheme(mode = themeMode) {
            SystemBarsEffect(darkTheme = isAppInDarkTheme)
            AppLockGate {
                NavSetup()
            }
        }
    }
}
