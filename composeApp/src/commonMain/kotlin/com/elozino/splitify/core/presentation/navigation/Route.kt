package com.elozino.splitify.core.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Route(val name: String, val isProtected: Boolean) {

    @Serializable
    object Onboarding : Route("onboarding", false)

    @Serializable
    object Auth : Route("login", false)

    @Serializable
    object Register : Route("register", false)

    @Serializable
    object Home : Route("home", true)

    @Serializable
    object Groups : Route("groups", true)

    @Serializable
    object Account : Route("profile", true)

    @Serializable
    object Contact : Route("contact", true)
}
