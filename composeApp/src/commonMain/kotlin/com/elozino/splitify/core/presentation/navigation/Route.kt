package com.elozino.splitify.core.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Route(val name: String, val isProtected: Boolean) {

    @Serializable
    object Onboarding : Route("onboarding", false)

    @Serializable
    object Auth : Route("auth", false)

    @Serializable
    object Login : Route("login", false)

    @Serializable
    object Signup : Route("signup", false)

    @Serializable
    object ForgotPassword : Route("forgot_password", false)

    @Serializable
    object NewPassword : Route("new_password", false)

    @Serializable
    object Otp : Route("otp", false)

    @Serializable
    object Home : Route("home", true)

    @Serializable
    object Groups : Route("groups", true)

    @Serializable
    object Account : Route("profile", true)

    @Serializable
    object Contact : Route("contact", true)

    @Serializable
    object Notification : Route("notification", true)

    @Serializable
    object Activity : Route("activity", true)
}
