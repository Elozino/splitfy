package com.elozino.splitify.core.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elozino.splitify.feature.auth.AuthScreen
import com.elozino.splitify.feature.auth.LoginScreen
import com.elozino.splitify.feature.auth.SignUpScreen
import com.elozino.splitify.feature.auth.ForgotPasswordScreen
import com.elozino.splitify.feature.auth.NewPasswordScreen
import com.elozino.splitify.feature.auth.OtpScreen
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
            AuthScreen(
                onNavigateToRegister = { navController.navigate(Route.Signup) },
                onNavigateToLogin = { navController.navigate(Route.Login) },
                onNavigateToSocialAuth = { /* Handle social auth */ }
            )
        }
        composable<Route.Signup> {
            SignUpScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.navigate(Route.Login) },
                onSignUpSuccess = { navController.navigate(Route.Home) }
            )
        }
        composable<Route.Login> {
            LoginScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSignup = { navController.navigate(Route.Signup) },
                onNavigateToForgotPassword = { navController.navigate(Route.ForgotPassword) },
                onLoginSuccess = { navController.navigate(Route.Home) }
            )
        }
        composable<Route.ForgotPassword> {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onOtpSent = { navController.navigate(Route.Otp) }
            )
        }
        composable<Route.Otp> {
            OtpScreen(
                onNavigateBack = { navController.popBackStack() },
                onOtpVerified = { navController.navigate(Route.NewPassword) }
            )
        }
        composable<Route.NewPassword> {
            NewPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onPasswordChanged = { navController.navigate(Route.Login) }
            )
        }
        composable<Route.Home> {}
        composable<Route.Groups> {}
        composable<Route.Account> {}
        composable<Route.Contact> {}
    }
}