package com.elozino.splitify.core.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elozino.splitify.core.presentation.component.MainBottomNavigation
import com.elozino.splitify.feature.account.AccountScreen
import com.elozino.splitify.feature.activity.ActivityScreen
import com.elozino.splitify.feature.auth.AuthScreen
import com.elozino.splitify.feature.auth.ForgotPasswordScreen
import com.elozino.splitify.feature.auth.LoginScreen
import com.elozino.splitify.feature.auth.NewPasswordScreen
import com.elozino.splitify.feature.auth.OtpScreen
import com.elozino.splitify.feature.auth.SignUpScreen
import com.elozino.splitify.feature.contact.ContactScreen
import com.elozino.splitify.feature.home.HomeScreen
import com.elozino.splitify.feature.notification.NotificationScreen
import com.elozino.splitify.feature.onboarding.presentation.OnboardingScreen
import com.elozino.splitify.feature.pay.PayScreenWrapper
import com.elozino.splitify.feature.request.RequestScreenWrapper
import com.elozino.splitify.feature.scan.ScanScreen
import com.elozino.splitify.feature.split.GroupsScreen

@Composable
fun NavSetup() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isAuthenticated = true

    val mainRoutes = listOf(Route.Home, Route.Groups, Route.Contact, Route.Account)
    val showBottomBar = currentDestination?.hierarchy?.any { dest ->
        mainRoutes.any { route -> dest.hasRoute(route::class) }
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val selectedTab = when {
                    currentDestination.hasRoute(Route.Home::class) -> 0
                    currentDestination.hasRoute(Route.Groups::class) -> 1
                    currentDestination.hasRoute(Route.Contact::class) -> 2
                    currentDestination.hasRoute(Route.Account::class) -> 3
                    else -> 0
                }
                MainBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { index ->
                        val targetRoute = when (index) {
                            0 -> Route.Home
                            1 -> Route.Groups
                            2 -> Route.Contact
                            3 -> Route.Account
                            else -> Route.Home
                        }
                        navController.navigate(targetRoute) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onScanClick = {
                        navController.navigate(Route.Scan)
                    }
                )
            }
        }
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = if (isAuthenticated) Route.Home else Route.Onboarding,
            modifier = Modifier.fillMaxSize()
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
            composable<Route.Home> {
                HomeScreen(
                    onNavigateToNotification = { navController.navigate(Route.Notification) },
                    onNavigateToActivity = { navController.navigate(Route.Activity) },
                    onNavigateToPay = { navController.navigate(Route.Pay) },
                    onNavigateToRequest = { navController.navigate(Route.Request) }
                )
            }
            composable<Route.Pay> {
                PayScreenWrapper(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Route.Request> {
                RequestScreenWrapper(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Route.Notification> {
                NotificationScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Route.Activity> {
                ActivityScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPay = { navController.navigate(Route.Pay) },
                    onNavigateToRequest = { navController.navigate(Route.Request) }
                )
            }
            composable<Route.Groups> {
                GroupsScreen()
            }
            composable<Route.Account> {
                AccountScreen()
            }
            composable<Route.Contact> {
                ContactScreen()
            }
            composable<Route.Scan> {
                ScanScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
