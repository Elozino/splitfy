package com.elozino.splitfy.invites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberShareInvite(): (String) -> Unit = remember {
    { url ->
        val window = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
        var presenter = window?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
        presenter?.let { controller ->
            val sheet = UIActivityViewController(listOf("Join my group on Splitfy: $url"), null)
            sheet.popoverPresentationController?.sourceView = controller.view
            sheet.popoverPresentationController?.sourceRect = controller.view.bounds
            controller.presentViewController(sheet, animated = true, completion = null)
        }
    }
}
