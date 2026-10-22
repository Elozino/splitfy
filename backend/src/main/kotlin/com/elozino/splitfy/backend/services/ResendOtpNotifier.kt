package com.elozino.splitfy.backend.services

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Sends one-time codes through Resend's HTTP API (https://resend.com/docs/api-reference/emails/send-email).
 *
 * [client] must have JSON content negotiation installed; it is injected so tests can
 * swap in a mock engine and production shares one connection pool.
 */
class ResendOtpNotifier(
    private val client: HttpClient,
    private val apiKey: String,
    private val from: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : OtpNotifier {

    private val logger = LoggerFactory.getLogger(ResendOtpNotifier::class.java)

    init {
        require(apiKey.isNotBlank()) { "Resend API key is required" }
        require(from.isNotBlank()) { "email.from is required" }
    }

    override suspend fun sendPasswordResetCode(email: String, code: String) =
        send(email, "Your Splitfy code: $code", OtpEmail.PasswordReset, code)

    override suspend fun sendEmailVerificationCode(email: String, code: String) =
        send(email, "Confirm your email: $code", OtpEmail.Verification, code)

    override suspend fun sendEmailChangeCode(email: String, code: String) =
        send(email, "Confirm your new email: $code", OtpEmail.EmailChange, code)

    override suspend fun sendEmailChangedNotice(oldEmail: String, newEmail: String) =
        deliver(oldEmail, "Your Splitfy email was changed", "EmailChanged", EmailChangedNotice.text(newEmail), EmailChangedNotice.html(newEmail))

    private suspend fun send(email: String, subject: String, kind: OtpEmail, code: String) =
        deliver(email, subject, kind.name, kind.text(code), kind.html(code))

    private suspend fun deliver(email: String, subject: String, kind: String, text: String, html: String) {
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                ResendEmail(
                    from = from,
                    to = listOf(email),
                    subject = subject,
                    text = text,
                    html = html,
                )
            )
        }
        if (!response.status.isSuccess()) {
            // The body names the problem (unverified domain, bad key) without echoing secrets.
            throw IllegalStateException("Resend rejected the email: ${response.status} ${response.bodyAsText().take(500)}")
        }
        logger.info("Sent {} email via Resend", kind)
    }

    @Serializable
    internal data class ResendEmail(
        val from: String,
        val to: List<String>,
        val subject: String,
        val text: String,
        val html: String,
        @SerialName("reply_to") val replyTo: String? = null,
    )

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.resend.com/emails"
    }
}

/** The code emails. Plain enough to survive any client, including text-only ones. */
internal enum class OtpEmail(private val heading: String, private val ignoreLine: String) {
    PasswordReset(
        heading = "Reset your Splitfy password",
        ignoreLine = "If you didn't ask to reset your password, you can ignore this email.",
    ),
    Verification(
        heading = "Confirm your email",
        ignoreLine = "If you didn't create a Splitfy account, you can ignore this email.",
    ),
    EmailChange(
        heading = "Confirm your new email",
        ignoreLine = "If you didn't ask to move a Splitfy account to this address, you can ignore this email.",
    );

    fun text(code: String) = """
        |$heading
        |
        |Your Splitfy code is $code
        |
        |It expires soon. $ignoreLine
    """.trimMargin()

    fun html(code: String) = """
        <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:420px;margin:0 auto;padding:24px;color:#111">
          <h1 style="font-size:20px;margin:0 0 16px">$heading</h1>
          <p style="margin:0 0 16px;color:#444">Enter this code in the app:</p>
          <p style="font-size:32px;letter-spacing:8px;font-weight:700;margin:0 0 16px">$code</p>
          <p style="margin:0;color:#777;font-size:13px">It expires soon. $ignoreLine</p>
        </div>
    """.trimIndent()
}

/** Sent to the old address after a change, so a hijacked session can't move the account silently. */
internal object EmailChangedNotice {
    private const val HEADING = "Your Splitfy email was changed"
    private const val WARNING =
        "If you didn't make this change, contact Splitfy support straight away so we can secure your account."

    fun text(newEmail: String) = """
        |$HEADING
        |
        |Your Splitfy account now signs in with $newEmail, and this address will no longer receive its emails.
        |
        |$WARNING
    """.trimMargin()

    fun html(newEmail: String) = """
        <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:420px;margin:0 auto;padding:24px;color:#111">
          <h1 style="font-size:20px;margin:0 0 16px">$HEADING</h1>
          <p style="margin:0 0 16px;color:#444">Your Splitfy account now signs in with <strong>${newEmail.escapeHtml()}</strong>, and this address will no longer receive its emails.</p>
          <p style="margin:0;color:#777;font-size:13px">$WARNING</p>
        </div>
    """.trimIndent()

    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
