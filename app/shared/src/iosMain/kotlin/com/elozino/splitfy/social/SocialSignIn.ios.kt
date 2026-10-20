package com.elozino.splitfy.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.elozino.splitfy.api.dto.OAuthProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

@Composable
actual fun rememberSocialSignIn(config: SocialSignInConfig): SocialSignIn =
    remember(config) { IosSocialSignIn(config) }

/** `GoogleIosClientId` in Info.plist, filled from GOOGLE_IOS_CLIENT_ID in the xcconfig. */
actual fun platformGoogleClientId(): String? =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("GoogleIosClientId") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.startsWith("$(") }

/**
 * Apple through the native sheet, Google through its web sign-in with PKCE. App
 * Store rule 4.8 requires Sign in with Apple wherever Google is offered, so Apple
 * comes first and is always present.
 */
private class IosSocialSignIn(private val config: SocialSignInConfig) : SocialSignIn {

    override val providers: List<OAuthProvider> = buildList {
        add(OAuthProvider.APPLE)
        if (config.googleClientId != null) add(OAuthProvider.GOOGLE)
    }

    override suspend fun signIn(provider: OAuthProvider): SocialSignInResult = when (provider) {
        OAuthProvider.APPLE -> signInWithApple()
        OAuthProvider.GOOGLE -> config.googleClientId?.let { signInWithGoogle(it) }
            ?: SocialSignInResult.Failure("Google sign-in isn't set up in this build")
    }
}

// --- Apple ------------------------------------------------------------------

private suspend fun signInWithApple(): SocialSignInResult = suspendCancellableCoroutine { continuation ->
    // Apple embeds the SHA-256 of our nonce; the server compares the token against it.
    val hashedNonce = sha256Hex(randomUrlSafe(32))
    val request = ASAuthorizationAppleIDProvider().createRequest().apply {
        requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
        nonce = hashedNonce
    }
    val controller = ASAuthorizationController(authorizationRequests = listOf(request))
    // The controller only holds its delegate weakly, so the coroutine keeps it alive.
    val delegate = AppleSignInDelegate(hashedNonce) { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    controller.delegate = delegate
    controller.presentationContextProvider = delegate
    // Referencing the delegate from the cancellation handler keeps it reachable
    // for as long as the sign-in is pending.
    continuation.invokeOnCancellation { delegate.hashCode() }
    controller.performRequests()
}

private class AppleSignInDelegate(
    private val nonce: String,
    private val onResult: (SocialSignInResult) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val token = credential?.identityToken?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }
        if (token == null) {
            onResult(SocialSignInResult.Failure("Apple didn't return a sign-in token"))
            return
        }
        // Apple shares the name with the app on the first sign-in only.
        val name = listOfNotNull(credential.fullName?.givenName, credential.fullName?.familyName)
            .joinToString(" ")
            .ifBlank { null }
        onResult(SocialSignInResult.Success(idToken = token, nonce = nonce, displayName = name))
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        onResult(
            if (didCompleteWithError.code == ASAuthorizationErrorCanceled) {
                SocialSignInResult.Cancelled
            } else {
                SocialSignInResult.Failure("Apple sign-in failed: ${didCompleteWithError.localizedDescription}")
            }
        )
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor =
        keyWindow()
}

// --- Google -----------------------------------------------------------------

/**
 * Authorization code with PKCE, as Google recommends for installed apps: no client
 * secret ships in the binary. The redirect uses the reversed client id as its URL
 * scheme, which ASWebAuthenticationSession intercepts without an Info.plist entry.
 */
private suspend fun signInWithGoogle(clientId: String): SocialSignInResult {
    val verifier = randomUrlSafe(48)
    val nonce = randomUrlSafe(32)
    val state = randomUrlSafe(16)
    val scheme = "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")
    val redirectUri = "$scheme:/oauth2redirect"

    val authUrl = NSURLComponents.componentsWithString("https://accounts.google.com/o/oauth2/v2/auth")!!.apply {
        queryItems = listOf(
            NSURLQueryItem("client_id", clientId),
            NSURLQueryItem("redirect_uri", redirectUri),
            NSURLQueryItem("response_type", "code"),
            NSURLQueryItem("scope", "openid email profile"),
            NSURLQueryItem("code_challenge", base64Url(sha256(verifier.encodeToByteArray()))),
            NSURLQueryItem("code_challenge_method", "S256"),
            NSURLQueryItem("nonce", nonce),
            NSURLQueryItem("state", state),
            NSURLQueryItem("prompt", "select_account"),
        )
    }.URL!!

    val callback = when (val outcome = runWebAuthentication(authUrl, scheme)) {
        is WebAuthOutcome.Callback -> outcome.url
        WebAuthOutcome.Cancelled -> return SocialSignInResult.Cancelled
        is WebAuthOutcome.Failed -> return SocialSignInResult.Failure("Google sign-in failed: ${outcome.message}")
    }

    val query = NSURLComponents.componentsWithURL(callback, resolvingAgainstBaseURL = false)
        ?.queryItems.orEmpty()
        .filterIsInstance<NSURLQueryItem>()
        .associate { it.name to it.value }
    if (query["state"] != state) return SocialSignInResult.Failure("Google sign-in was interrupted. Please try again.")
    val code = query["code"] ?: return SocialSignInResult.Cancelled

    return exchangeCode(clientId, code, verifier, redirectUri, nonce)
}

private suspend fun exchangeCode(
    clientId: String,
    code: String,
    verifier: String,
    redirectUri: String,
    nonce: String,
): SocialSignInResult = HttpClient(Darwin).use { client ->
    val response = client.submitForm(
        url = "https://oauth2.googleapis.com/token",
        formParameters = Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
            append("client_id", clientId)
            append("code_verifier", verifier)
            append("redirect_uri", redirectUri)
        },
    )
    if (!response.status.isSuccess()) {
        return@use SocialSignInResult.Failure("Google sign-in failed (${response.status.value})")
    }
    val idToken = Json.parseToJsonElement(response.bodyAsText()).jsonObject["id_token"]?.jsonPrimitive?.content
        ?: return@use SocialSignInResult.Failure("Google didn't return a sign-in token")
    SocialSignInResult.Success(idToken = idToken, nonce = nonce)
}

private sealed interface WebAuthOutcome {
    data class Callback(val url: NSURL) : WebAuthOutcome
    data object Cancelled : WebAuthOutcome
    data class Failed(val message: String) : WebAuthOutcome
}

private suspend fun runWebAuthentication(url: NSURL, callbackScheme: String): WebAuthOutcome =
    suspendCancellableCoroutine { continuation ->
        val anchor = WebAuthAnchor()
        val session = ASWebAuthenticationSession(url, callbackScheme) { callback, error ->
            if (!continuation.isActive) return@ASWebAuthenticationSession
            continuation.resume(
                when {
                    callback != null -> WebAuthOutcome.Callback(callback)
                    error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> WebAuthOutcome.Cancelled
                    else -> WebAuthOutcome.Failed(error?.localizedDescription ?: "unknown error")
                }
            )
        }
        session.presentationContextProvider = anchor
        continuation.invokeOnCancellation { session.cancel() }
        session.start()
    }

private class WebAuthAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        keyWindow()
}

// --- helpers ----------------------------------------------------------------

private fun keyWindow(): UIWindow =
    UIApplication.sharedApplication.keyWindow
        ?: UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().first()

@OptIn(ExperimentalForeignApi::class)
private fun randomUrlSafe(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, byteCount.convert(), it.addressOf(0)) }
    return base64Url(bytes)
}

@OptIn(ExperimentalForeignApi::class)
private fun sha256(input: ByteArray): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { data ->
        digest.usePinned { out ->
            CC_SHA256(if (input.isEmpty()) null else data.addressOf(0), input.size.convert(), out.addressOf(0).reinterpret())
        }
    }
    return digest
}

private fun sha256Hex(input: String): String =
    sha256(input.encodeToByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun base64Url(bytes: ByteArray): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
