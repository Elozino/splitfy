package com.elozino.splitfy.backend.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** What a push says. [data] travels to the app untouched, for routing a tap. */
data class PushMessage(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
)

enum class PushResult {
    SENT,

    /** The token will never work again (app uninstalled, token rotated): forget it. */
    INVALID_TOKEN,

    /** Anything transient or on our side. The token is kept. */
    FAILED,
}

/** Delivers one push to one device. Implementations never throw for delivery problems. */
interface PushSender {
    suspend fun send(token: String, message: PushMessage): PushResult
}

/**
 * Used when FCM is not configured. Push is optional, so an unconfigured server
 * still boots and keeps in-app notifications; it only logs what it would have sent.
 */
class LoggingPushSender : PushSender {
    private val logger = LoggerFactory.getLogger(LoggingPushSender::class.java)

    override suspend fun send(token: String, message: PushMessage): PushResult {
        logger.debug("Push not configured; would send '{}' to {}…", message.title, token.take(8))
        return PushResult.SENT
    }
}

/** The fields we need from a Google service-account key file. */
@Serializable
data class FcmServiceAccount(
    @SerialName("project_id") val projectId: String,
    @SerialName("client_email") val clientEmail: String,
    @SerialName("private_key") val privateKey: String,
    @SerialName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): FcmServiceAccount = json.decodeFromString(serializer(), raw)
    }
}

/**
 * FCM HTTP v1 (https://firebase.google.com/docs/cloud-messaging/send/v1-api).
 *
 * Authenticates as the service account with the OAuth 2.0 JWT bearer grant: a
 * self-signed RS256 assertion is exchanged for a one-hour access token, which is
 * cached and shared by every send until shortly before it expires. iOS devices are
 * reached through the same API, because Firebase relays to APNs with the key
 * uploaded in the Firebase console.
 *
 * [client] must have JSON content negotiation installed; it is injected so tests can
 * use a mock engine.
 */
class FcmPushSender(
    private val client: HttpClient,
    private val account: FcmServiceAccount,
    private val clock: Clock = Clock.System,
    private val endpoint: String = "https://fcm.googleapis.com/v1/projects/${account.projectId}/messages:send",
) : PushSender {

    private val logger = LoggerFactory.getLogger(FcmPushSender::class.java)
    private val signingKey: RSAPrivateKey = parsePrivateKey(account.privateKey)
    private val tokenLock = Mutex()
    private var cached: AccessToken? = null

    override suspend fun send(token: String, message: PushMessage): PushResult = try {
        var response = post(token, message, accessToken())
        if (response.status == HttpStatusCode.Unauthorized) {
            // Revoked or rotated key mid-hour: fetch a fresh token once before giving up.
            invalidate()
            response = post(token, message, accessToken())
        }
        classify(response)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        logger.warn("FCM send failed: {}", e.message)
        PushResult.FAILED
    }

    private suspend fun post(token: String, message: PushMessage, accessToken: String): HttpResponse =
        client.post(endpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(FcmRequest(FcmMessage.of(token, message)))
        }

    private suspend fun classify(response: HttpResponse): PushResult {
        if (response.status.isSuccess()) return PushResult.SENT
        val text = response.bodyAsText()
        val error = runCatching { lenient.decodeFromString(FcmErrorResponse.serializer(), text).error }.getOrNull()
        val codes = error?.details.orEmpty().mapNotNull { it.errorCode }.toSet()
        // Not a bare 404: a wrong project id is also a 404, and must not wipe every token.
        val invalid = "UNREGISTERED" in codes ||
            // FCM answers a malformed token with INVALID_ARGUMENT and names the token
            // field; a bad payload uses the same code but a different message.
            ("INVALID_ARGUMENT" in codes && error?.message.orEmpty().contains("registration token", ignoreCase = true))
        if (invalid) return PushResult.INVALID_TOKEN
        logger.warn("FCM rejected a push: {} {}", response.status, text.take(300))
        return PushResult.FAILED
    }

    private suspend fun accessToken(): String = tokenLock.withLock {
        cached?.takeIf { clock.now() < it.expiresAt - EXPIRY_MARGIN }?.let { return it.value }
        val now = clock.now()
        val assertion = JWT.create()
            .withIssuer(account.clientEmail)
            .withAudience(account.tokenUri)
            .withClaim("scope", SCOPE)
            .withIssuedAt(Date(now.toEpochMilliseconds()))
            .withExpiresAt(Date((now + 3600.seconds).toEpochMilliseconds()))
            .sign(Algorithm.RSA256(null, signingKey))
        val response = client.submitForm(
            url = account.tokenUri,
            formParameters = parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", assertion)
            },
        )
        check(response.status.isSuccess()) {
            "Google refused the FCM service account: ${response.status} ${response.bodyAsText().take(300)}"
        }
        // Decoded here rather than by content negotiation: the shared client's Json
        // rejects unknown keys, and Google's response carries more than we read.
        val body = lenient.decodeFromString(OAuthTokenResponse.serializer(), response.bodyAsText())
        AccessToken(body.accessToken, now + body.expiresIn.seconds).also { cached = it }.value
    }

    private suspend fun invalidate() = tokenLock.withLock { cached = null }

    private data class AccessToken(val value: String, val expiresAt: Instant)

    @Serializable
    internal data class OAuthTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 3600,
    )

    @Serializable
    internal data class FcmRequest(val message: FcmMessage)

    @Serializable
    internal data class FcmMessage(
        val token: String,
        val notification: Notification,
        val data: Map<String, String>,
        val android: Android,
        val apns: Apns,
    ) {
        @Serializable
        data class Notification(val title: String, val body: String)

        @Serializable
        data class Android(val priority: String, val notification: AndroidNotification)

        @Serializable
        data class AndroidNotification(@SerialName("channel_id") val channelId: String)

        @Serializable
        data class Apns(val payload: ApnsPayload)

        @Serializable
        data class ApnsPayload(val aps: Aps)

        @Serializable
        data class Aps(val sound: String)

        companion object {
            fun of(token: String, message: PushMessage) = FcmMessage(
                token = token,
                notification = Notification(message.title, message.body),
                data = message.data,
                android = Android(priority = "high", notification = AndroidNotification(ANDROID_CHANNEL_ID)),
                apns = Apns(ApnsPayload(Aps(sound = "default"))),
            )
        }
    }

    @Serializable
    internal data class FcmErrorResponse(val error: FcmError? = null)

    @Serializable
    internal data class FcmError(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null,
        val details: List<FcmErrorDetail> = emptyList(),
    )

    @Serializable
    internal data class FcmErrorDetail(val errorCode: String? = null)

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

        /** Must match the channel the Android app creates. */
        const val ANDROID_CHANNEL_ID = "splitfy_default"

        private val EXPIRY_MARGIN = 60.seconds
        private val lenient = Json { ignoreUnknownKeys = true }

        internal fun parsePrivateKey(pem: String): RSAPrivateKey {
            val base64 = pem
                .replace("\\n", "\n")
                .lineSequence()
                .filterNot { it.startsWith("-----") }
                .joinToString("") { it.trim() }
            val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))
            return KeyFactory.getInstance("RSA").generatePrivate(spec) as RSAPrivateKey
        }
    }
}

/**
 * Picks the sender for this deployment. Push is optional: no key, or a key that
 * cannot be read, means a logging sender and a warning, never a failed boot.
 */
fun pushSender(config: com.elozino.splitfy.backend.utils.AppConfig.PushConfig, client: HttpClient): PushSender {
    val logger = LoggerFactory.getLogger(PushSender::class.java)
    if (!config.isFcmConfigured) {
        logger.info("FCM is not configured; push notifications will only be logged")
        return LoggingPushSender()
    }
    return runCatching {
        val raw = config.fcmServiceAccountJson.ifBlank { java.io.File(config.fcmServiceAccountPath).readText() }
        FcmPushSender(client, FcmServiceAccount.parse(raw))
    }.onSuccess {
        logger.info("Sending push notifications through FCM")
    }.getOrElse {
        logger.error("FCM service account could not be loaded ({}); push notifications will only be logged", it.message)
        LoggingPushSender()
    }
}
