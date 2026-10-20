package com.elozino.splitfy.social

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.elozino.splitfy.api.dto.OAuthProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom
import kotlin.io.encoding.Base64

@Composable
actual fun rememberSocialSignIn(config: SocialSignInConfig): SocialSignIn {
    val context = LocalContext.current
    return remember(context, config) { AndroidSocialSignIn(context, config) }
}

/** The web client id arrives through `initKoin` from the app's BuildConfig instead. */
actual fun platformGoogleClientId(): String? = null

/**
 * Google through Credential Manager. Apple is not offered on Android: it has no
 * native sheet here, and App Store rules only require it where Google is offered on iOS.
 */
private class AndroidSocialSignIn(
    private val context: Context,
    private val config: SocialSignInConfig,
) : SocialSignIn {

    override val providers: List<OAuthProvider> =
        if (config.googleClientId != null) listOf(OAuthProvider.GOOGLE) else emptyList()

    override suspend fun signIn(provider: OAuthProvider): SocialSignInResult {
        val clientId = config.googleClientId
        if (provider != OAuthProvider.GOOGLE || clientId == null) {
            return SocialSignInResult.Failure("That sign-in option isn't available here")
        }
        val activity = context.findActivity()
            ?: return SocialSignInResult.Failure("Couldn't open the Google sign-in sheet")

        // A fresh nonce per attempt, echoed in the token and checked by the server.
        val nonce = randomNonce()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                    .setNonce(nonce)
                    .build()
            )
            .build()

        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                SocialSignInResult.Success(idToken = google.idToken, nonce = nonce, displayName = google.displayName)
            } else {
                SocialSignInResult.Failure("Google returned an unexpected credential")
            }
        } catch (_: GetCredentialCancellationException) {
            SocialSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            SocialSignInResult.Failure("Add a Google account to this device to continue with Google")
        } catch (e: GetCredentialException) {
            SocialSignInResult.Failure("Google sign-in failed: ${e.message ?: e.type}")
        } catch (_: GoogleIdTokenParsingException) {
            SocialSignInResult.Failure("Google returned a token we couldn't read")
        }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
