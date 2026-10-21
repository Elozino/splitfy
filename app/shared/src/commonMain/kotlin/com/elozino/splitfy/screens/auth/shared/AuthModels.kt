package com.elozino.splitfy.screens.auth.shared

import com.elozino.splitfy.api.dto.OAuthProvider

/** How a landing button is painted; resolved to theme colours where it is drawn. */
enum class AuthButtonStyle { Surface, Brand, Outlined }

enum class AuthType {
    Google,
    Apple,
    Email,
}

data class AuthOption(
    val title: String,
    val type: AuthType,
    val iconUri: String,
    val style: AuthButtonStyle,
    val isPrimary: Boolean = false
)

val authOptions = listOf(
    AuthOption("Continue with Google", AuthType.Google, "drawable/ic_google.svg", AuthButtonStyle.Surface),
    AuthOption("Continue with Apple", AuthType.Apple, "drawable/ic_apple.svg", AuthButtonStyle.Surface),
    AuthOption("Sign up", AuthType.Email, "drawable/ic_email.svg", AuthButtonStyle.Brand, isPrimary = true),
    AuthOption("Log in", AuthType.Email, "drawable/ic_email.svg", AuthButtonStyle.Outlined, isPrimary = false),
)

/** The landing-screen button for a provider the platform can offer. */
fun OAuthProvider.authOption(): AuthOption = authOptions.first {
    it.type == when (this) {
        OAuthProvider.GOOGLE -> AuthType.Google
        OAuthProvider.APPLE -> AuthType.Apple
    }
}
