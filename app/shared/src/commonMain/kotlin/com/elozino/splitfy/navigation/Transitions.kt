package com.elozino.splitfy.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/**
 * How screens move, in one place. Each kind of navigation has its own motion, so the
 * animation says what happened:
 *
 * - **Push** (drill into a group, open settings): the new screen slides in from the
 *   end edge over the old one, which drifts back a quarter width. Back reverses it.
 * - **Sheet** (add an expense, settle up, create a group): rises from the bottom over
 *   the current screen and drops back down when dismissed. It reads as a task you
 *   finish and leave, not a place you go.
 * - **Tab switch**: a quick fade-through (out, then in with a slight zoom), so the
 *   bottom-bar tabs feel like peers rather than a stack.
 * - **Hand-off** (onboarding to sign-in, sign-in to Home): a plain crossfade; the whole
 *   context changes, so nothing should look like it is "behind" anything else.
 *
 * Timings follow Material 3: about 300 ms with the emphasized easing curves, short
 * enough to feel immediate, long enough to read the direction.
 */
internal object Motion {
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    private const val PUSH_MS = 320
    private const val SHEET_IN_MS = 360
    private const val SHEET_OUT_MS = 240
    private const val FADE_OUT_MS = 90
    private const val FADE_IN_MS = 210
    private const val HANDOFF_MS = 280

    /** Bottom-bar destinations: switching between them is a tab change, not a push. */
    private val tabs: List<KClass<out Route>> =
        listOf(Route.Home::class, Route.Groups::class, Route.Contact::class, Route.Account::class)

    /** Task flows presented as sheets over the screen that opened them. */
    private val sheets: List<KClass<out Route>> = listOf(
        Route.AddExpense::class, Route.SettleUp::class, Route.NewGroup::class,
        Route.ShareInvite::class, Route.Scan::class,
    )

    /** Leaving or entering these replaces the whole context. */
    private val handOffs: List<KClass<out Route>> = listOf(
        Route.Onboarding::class, Route.Auth::class, Route.VerifyEmail::class, Route.TwoFactorSignIn::class,
    )

    private fun NavDestination.isAny(routes: List<KClass<out Route>>) = routes.any { hasRoute(it) }

    private enum class Kind { TAB, SHEET, HANDOFF, PUSH }

    /** [moving] is the screen the transition is about: the one arriving, or the one leaving on back. */
    private fun kind(from: NavDestination, to: NavDestination, moving: NavDestination): Kind = when {
        from.isAny(tabs) && to.isAny(tabs) -> Kind.TAB
        moving.isAny(sheets) -> Kind.SHEET
        // Signing in lands on Home with the auth graph popped: a context change.
        (from.isAny(handOffs) || to.isAny(handOffs)) && (from.isAny(tabs) || to.isAny(tabs)) -> Kind.HANDOFF
        from.hasRoute(Route.Onboarding::class) || to.hasRoute(Route.Onboarding::class) -> Kind.HANDOFF
        else -> Kind.PUSH
    }

    private fun fadeThroughIn() =
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = LinearEasing)) +
            scaleIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = Emphasized), initialScale = 0.96f)

    private fun fadeThroughOut() = fadeOut(tween(FADE_OUT_MS, easing = LinearEasing))

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val from = initialState.destination
        val to = targetState.destination
        when (kind(from, to, moving = to)) {
            Kind.TAB -> fadeThroughIn()
            Kind.SHEET -> slideInVertically(tween(SHEET_IN_MS, easing = EmphasizedDecelerate)) { it } +
                fadeIn(tween(SHEET_IN_MS / 2))
            Kind.HANDOFF -> fadeIn(tween(HANDOFF_MS, easing = LinearEasing))
            Kind.PUSH -> slideInHorizontally(tween(PUSH_MS, easing = EmphasizedDecelerate)) { it }
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val from = initialState.destination
        val to = targetState.destination
        when (kind(from, to, moving = to)) {
            Kind.TAB -> fadeThroughOut()
            // The screen under a sheet stays put; the sheet covers it.
            Kind.SHEET -> fadeOut(tween(SHEET_IN_MS), targetAlpha = 0.9f)
            Kind.HANDOFF -> fadeOut(tween(HANDOFF_MS, easing = LinearEasing))
            Kind.PUSH -> slideOutHorizontally(tween(PUSH_MS, easing = EmphasizedDecelerate)) { -it / 4 } +
                fadeOut(tween(PUSH_MS), targetAlpha = 0.85f)
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val from = initialState.destination
        val to = targetState.destination
        when (kind(from, to, moving = from)) {
            Kind.TAB -> fadeThroughIn()
            Kind.SHEET -> fadeIn(tween(SHEET_OUT_MS), initialAlpha = 0.9f)
            Kind.HANDOFF -> fadeIn(tween(HANDOFF_MS, easing = LinearEasing))
            Kind.PUSH -> slideInHorizontally(tween(PUSH_MS, easing = EmphasizedDecelerate)) { -it / 4 } +
                fadeIn(tween(PUSH_MS), initialAlpha = 0.85f)
        }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val from = initialState.destination
        val to = targetState.destination
        when (kind(from, to, moving = from)) {
            Kind.TAB -> fadeThroughOut()
            Kind.SHEET -> slideOutVertically(tween(SHEET_OUT_MS, easing = EmphasizedAccelerate)) { it } +
                fadeOut(tween(SHEET_OUT_MS, delayMillis = SHEET_OUT_MS / 2))
            Kind.HANDOFF -> fadeOut(tween(HANDOFF_MS, easing = LinearEasing))
            Kind.PUSH -> slideOutHorizontally(tween(PUSH_MS, easing = EmphasizedDecelerate)) { it }
        }
    }

    /** The bottom bar slides with the screens rather than popping in and out. */
    val barEnter: EnterTransition = slideInVertically(tween(PUSH_MS, easing = EmphasizedDecelerate)) { it }
    val barExit: ExitTransition = slideOutVertically(tween(PUSH_MS / 2, easing = EmphasizedAccelerate)) { it }
}
