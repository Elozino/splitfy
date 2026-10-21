package com.elozino.splitfy.sharing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberShareFile(): (fileName: String, bytes: ByteArray, mimeType: String) -> Unit = remember {
    { fileName, bytes, _ ->
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        data.writeToURL(url, atomically = true)

        val window = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
        var presenter = window?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
        presenter?.let { controller ->
            val sheet = UIActivityViewController(listOf(url), null)
            sheet.popoverPresentationController?.sourceView = controller.view
            sheet.popoverPresentationController?.sourceRect = controller.view.bounds
            controller.presentViewController(sheet, animated = true, completion = null)
        }
    }
}
