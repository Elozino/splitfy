package com.elozino.splitify.feature.auth

import androidx.compose.ui.graphics.Color

enum class AuthType {
    Google,
    Facebook,
    Apple,
    Twitter,
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
    AuthOption("Continue with Facebook", AuthType.Facebook, "drawable/ic_facebook.svg", Color.White),
    AuthOption("Continue with Twitter", AuthType.Twitter, "drawable/ic_twitter.svg", Color.White),
    AuthOption("Sign up", AuthType.Email, "drawable/ic_email.svg", Color(0xFFFEBB1B), isPrimary = true),
    AuthOption("Log in", AuthType.Email, "drawable/ic_email.svg", Color.Transparent, isPrimary = false),
)
