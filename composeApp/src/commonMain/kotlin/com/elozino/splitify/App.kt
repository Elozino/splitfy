package com.elozino.splitify

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.elozino.splitify.core.presentation.navigation.NavSetup
import com.elozino.splitify.ui.theme.AppTheme
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

        AppTheme {
            NavSetup()
        }
    }
}
