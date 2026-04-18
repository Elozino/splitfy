package com.elozino.splitify.core.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elozino.splitify.feature.auth.AuthScreen
import com.elozino.splitify.feature.onboarding.presentation.OnboardingScreen


@Composable
fun NavSetup(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    val isAuthenticated = false

    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated) Route.Home else Route.Onboarding,
        modifier = modifier.fillMaxSize()
    ) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                onNavigateToAuth = {
                    navController.navigate(Route.Auth)
                }
            )
        }
        composable<Route.Auth> {
            AuthScreen()
        }
        composable<Route.Register> {}
        composable<Route.Home> {}
        composable<Route.Groups> {}
        composable<Route.Account> {}
        composable<Route.Contact> {}
    }
}