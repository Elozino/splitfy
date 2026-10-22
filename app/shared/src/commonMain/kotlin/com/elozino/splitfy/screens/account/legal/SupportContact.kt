package com.elozino.splitfy.screens.account.legal

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.elozino.splitfy.getPlatform
import io.ktor.http.encodeURLParameter

/** Where users reach a human. Must be a monitored inbox before release. */
object SupportContact {

    const val EMAIL = "support@splitfy.app"

    /**
     * A `mailto:` link with the subject filled in and the platform in the body, so a
     * report says which app it came from. Nothing about the user is added: they
     * choose what to write.
     */
    fun mailto(subject: String, platform: String): String {
        val body = "\n\n—\nSent from Splitfy on $platform"
        return "mailto:$EMAIL?subject=${subject.encodeURLParameter()}&body=${body.encodeURLParameter()}"
    }
}

/**
 * Returns a function that opens the mail app addressed to support. It reports
 * whether that worked, so the caller can fall back to showing the address when the
 * device has no mail app (common on Android emulators and tablets).
 */
@Composable
fun rememberSupportMailer(): (subject: String) -> Boolean {
    val uriHandler = LocalUriHandler.current
    return { subject ->
        runCatching { uriHandler.openUri(SupportContact.mailto(subject, getPlatform().name)) }.isSuccess
    }
}
