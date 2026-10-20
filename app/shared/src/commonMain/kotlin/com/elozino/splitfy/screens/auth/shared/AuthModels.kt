package com.elozino.splitfy.screens.auth.shared

import androidx.compose.ui.graphics.Color
import com.elozino.splitfy.api.dto.OAuthProvider

enum class AuthType {
    Google,
    Apple,
    Email,
}

data class AuthOption(
    val title: String,
    val type: AuthType,
    val iconUri: String,
    val backgroundColor: Color,
    val isPrimary: Boolean = false
)

val authOptions = listOf(
    AuthOption("Continue with Google", AuthType.Google, "drawable/ic_google.svg", Color.White),
    AuthOption("Continue with Apple", AuthType.Apple, "drawable/ic_apple.svg", Color.White),
    AuthOption("Sign up", AuthType.Email, "drawable/ic_email.svg", Color(0xFFFEBB1B), isPrimary = true),
    AuthOption("Log in", AuthType.Email, "drawable/ic_email.svg", Color.Transparent, isPrimary = false),
)

/** The landing-screen button for a provider the platform can offer. */
fun OAuthProvider.authOption(): AuthOption = authOptions.first {
    it.type == when (this) {
        OAuthProvider.GOOGLE -> AuthType.Google
        OAuthProvider.APPLE -> AuthType.Apple
    }
}
