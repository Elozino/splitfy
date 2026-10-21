package com.elozino.splitfy.sharing

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberShareFile(): (fileName: String, bytes: ByteArray, mimeType: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { fileName, bytes, mimeType ->
            // Must match the cache-path in androidApp's res/xml/shared_files.xml.
            val folder = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(folder, fileName).apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, fileName))
        }
    }
}
