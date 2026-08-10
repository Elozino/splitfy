package com.elozino.splitify.screens

data class OnboardingState(
    val title: String,
    val subtitle: String,
    val image: String = "",
)

val onboardingSlides = listOf(
    OnboardingState(
        title = "Track shared expenses effortlessly",
        subtitle = "Add bills, split costs, and keep everything organized in one place."
    ),
    OnboardingState(
        title = "Know who owes what, instantly",
        subtitle = "No more confusion — Splitify calculates balances for you in real time."
    ),
    OnboardingState(
        title = "Settle up with confidence",
        subtitle = "Simplify debts and close payments quickly without stress or awkward reminders."
    )
)
