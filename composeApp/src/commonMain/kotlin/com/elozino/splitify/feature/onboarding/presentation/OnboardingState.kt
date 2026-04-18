package com.elozino.splitify.feature.onboarding.presentation

data class OnboardingState(
    val title: String,
    val subtitle: String,
    val image: String = "",
)

val onboardingSlides = listOf<OnboardingState>(
    OnboardingState(
        title = "Seamless payments, Effortless splits",
        subtitle = "Splitify makes it easy to manage shared expenses with friends and family.",
    ),
    OnboardingState(
        title = "Seamless payments, Effortless splits",
        subtitle = "Splitify makes it easy to manage shared expenses with friends and family.",
    ),
    OnboardingState(
        title = "Seamless payments, Effortless splits",
        subtitle = "Splitify makes it easy to manage shared expenses with friends and family.",
    )
)
