package com.elozino.splitfy.screens.onboarding.main

import com.elozino.splitfy.ui.generated.resources.UiRes
import com.elozino.splitfy.ui.generated.resources.onboarding_activity
import com.elozino.splitfy.ui.generated.resources.onboarding_groups
import com.elozino.splitfy.ui.generated.resources.onboarding_wallet
import org.jetbrains.compose.resources.DrawableResource

data class OnboardingState(
    val title: String,
    val subtitle: String,
    val badge: String,
    val imageDescription: String,
    /**
     * A real screenshot of the screen this slide describes, shown in a phone frame
     * above the copy. Captured from the running app with the system status bar and
     * gesture pill cropped off; all three share one aspect ratio so the frame does
     * not resize as the pager moves between slides.
     */
    val image: DrawableResource,
)

val onboardingSlides = listOf(
    OnboardingState(
        title = "Split the cost.\nKeep the vibe.",
        subtitle = "Create a group, add expenses, and let Splitfy keep every shared cost crystal clear.",
        badge = "Everyone's in sync",
        imageDescription = "A preview of shared expense groups",
        image = UiRes.drawable.onboarding_groups,
    ),
    OnboardingState(
        title = "Always know\nwho owes what.",
        subtitle = "Balances update as you go, so there’s no maths, confusion, or awkward group-chat audits.",
        badge = "Balances updated",
        imageDescription = "A preview of expense activity and balances",
        image = UiRes.drawable.onboarding_activity,
    ),
    OnboardingState(
        title = "Settle up.\nMove on.",
        subtitle = "See what you owe and close the loop in a few taps—simple, quick, and drama-free.",
        badge = "All settled",
        imageDescription = "A preview of the Splitfy wallet",
        image = UiRes.drawable.onboarding_wallet,
    )
)

