package com.elozino.splitfy.sharing

import androidx.compose.runtime.Composable

/**
 * Opens the system share sheet for a file, so the user can save it to Files/Drive,
 * mail it to themselves, or open it in another app. The bytes are written to the
 * app's cache first and replaced by the next share of the same name.
 */
@Composable
expect fun rememberShareFile(): (fileName: String, bytes: ByteArray, mimeType: String) -> Unit
