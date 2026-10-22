package com.elozino.splitfy.social

import androidx.compose.runtime.Composable
import com.elozino.splitfy.api.dto.OAuthProvider

/**
 * OAuth client ids baked into the build.
 *
 * On Android, [googleClientId] is the **web** client id: Credential Manager asks
 * Google for a token addressed to it, which is what the server verifies. On iOS it is
 * the iOS client id used for the PKCE flow. Null means Google sign-in is not
 * offered in this build.
 */
data class SocialSignInConfig(val googleClientId: String?)

sealed interface SocialSignInResult {
    /** An ID token for the server to verify, plus what the provider only tells the app. */
    data class Success(
        val idToken: String,
        val nonce: String? = null,
        val displayName: String? = null,
    ) : SocialSignInResult

    /** The user closed the sheet. Not an error, so nothing is shown. */
    data object Cancelled : SocialSignInResult

    /**
     * Shown to the user as is, so it must be plain words: platform error text
     * (NSError descriptions, exception messages) goes to the log, never here.
     */
    data class Failure(val message: String) : SocialSignInResult
}

/** Platform sign-in sheets: Google on Android; Apple and Google on iOS. */
interface SocialSignIn {
    /** Providers this build can offer, in display order. */
    val providers: List<OAuthProvider>

    suspend fun signIn(provider: OAuthProvider): SocialSignInResult
}

/** Bound to the current screen, since the sheets need the Activity or window to present from. */
@Composable
expect fun rememberSocialSignIn(config: SocialSignInConfig): SocialSignIn

/** The Google client id the platform build carries, when the host app did not pass one in. */
expect fun platformGoogleClientId(): String?
