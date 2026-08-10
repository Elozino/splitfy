package com.elozino.splitify.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.toRoute
import com.elozino.splitify.screens.*
import com.elozino.splitify.screens.ActivityScreen
import com.elozino.splitify.screens.AddExpenseScreen
import com.elozino.splitify.screens.AuthScreen
import com.elozino.splitify.screens.AuthViewModel
import com.elozino.splitify.screens.ContactScreen
import com.elozino.splitify.screens.ExpenseDetailScreen
import com.elozino.splitify.screens.ForgotPasswordScreen
import com.elozino.splitify.screens.GroupDetailScreen
import com.elozino.splitify.screens.GroupSettingsScreen
import com.elozino.splitify.screens.GroupsScreen
import com.elozino.splitify.screens.HomeScreen
import com.elozino.splitify.screens.LoginScreen
import com.elozino.splitify.screens.NewGroupScreen
import com.elozino.splitify.screens.NewGroupViewModel
import com.elozino.splitify.screens.NewPasswordScreen
import com.elozino.splitify.screens.NotificationScreen
import com.elozino.splitify.screens.OnboardingScreen
import com.elozino.splitify.screens.OtpScreen
import com.elozino.splitify.screens.PayScreenWrapper
import com.elozino.splitify.screens.RequestScreenWrapper
import com.elozino.splitify.screens.ScanScreen
import com.elozino.splitify.screens.SelectParticipantsScreen
import com.elozino.splitify.screens.SignUpScreen
import com.elozino.splitify.screens.TopUpScreen
import com.elozino.splitify.screens.TransactionHistoryScreen
import com.elozino.splitify.screens.WithdrawScreen
import com.elozino.splitify.SessionManager
import com.elozino.splitify.SessionState
import com.elozino.splitify.ui.components.MainBottomNavigation
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NavSetup() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var showBottomBarOverride by remember { mutableStateOf(true) }

    LaunchedEffect(currentDestination) {
        showBottomBarOverride = true
    }

    // The session is the single source of truth for which half of the graph the user
    // belongs in — the flag used to be hardcoded to `true`.
    // One AuthViewModel for the whole unauthenticated graph: the reset flow spans
    // three screens and has to carry the email and reset token between them.
    val authViewModel: AuthViewModel = koinViewModel()
    // The create-group draft spans two screens, so it lives above both of them.
    val newGroupViewModel: NewGroupViewModel = koinViewModel()
    val sessionManager: SessionManager = koinInject()
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    val isAuthenticated = sessionState is SessionState.SignedIn

    // A 401 anywhere in the app (or an explicit sign-out) unwinds the whole back
    // stack exactly once, so no authenticated screen is left behind it.
    var wasSignedIn by remember { mutableStateOf(isAuthenticated) }
    LaunchedEffect(sessionState) {
        val signedIn = sessionState is SessionState.SignedIn
        if (wasSignedIn && !signedIn) {
            navController.navigate(Route.Auth) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
        wasSignedIn = signedIn
    }

    val showBottomBar = showBottomBarOverride && (
            currentDestination?.hasRoute(Route.Home::class) == true ||
            currentDestination?.hasRoute(Route.Groups::class) == true ||
            currentDestination?.hasRoute(Route.Contact::class) == true ||
            currentDestination?.hasRoute(Route.Account::class) == true
    )

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
            modifier = Modifier
                .fillMaxSize()
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
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = { navController.navigate(Route.Login) },
                    onSignUpSuccess = { navController.navigate(Route.Home) }
                )
            }
            composable<Route.Login> {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSignup = { navController.navigate(Route.Signup) },
                    onNavigateToForgotPassword = { navController.navigate(Route.ForgotPassword) },
                    onLoginSuccess = { navController.navigate(Route.Home) }
                )
            }
            composable<Route.ForgotPassword> {
                ForgotPasswordScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOtpSent = { navController.navigate(Route.Otp) }
                )
            }
            composable<Route.Otp> {
                OtpScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOtpVerified = { navController.navigate(Route.NewPassword) }
                )
            }
            composable<Route.NewPassword> {
                NewPasswordScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPasswordChanged = { navController.navigate(Route.Login) }
                )
            }
            composable<Route.Home> {
                HomeScreen(
                    onNavigateToNotification = { navController.navigate(Route.Notification) },
                    onNavigateToActivity = { navController.navigate(Route.Activity) },
                    onNavigateToPay = { navController.navigate(Route.Pay) },
                    onNavigateToRequest = { navController.navigate(Route.Request) },
                    onNavigateToTopUp = { navController.navigate(Route.TopUp) },
                    onNavigateToWithdraw = { navController.navigate(Route.Withdraw) },
                    onNavigateToTransactionHistory = { navController.navigate(Route.TransactionHistory) }
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
            composable<Route.TopUp> {
                TopUpScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Route.Withdraw> {
                WithdrawScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Route.TransactionHistory> {
                TransactionHistoryScreen(
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
                GroupsScreen(
                    onNavigateToNewGroup = { navController.navigate(Route.NewGroup) },
                    onNavigateToGroupDetail = { groupId -> 
                        navController.navigate(Route.GroupDetail(groupId)) 
                    }
                )
            }
            composable<Route.NewGroup> {
                NewGroupScreen(
                    viewModel = newGroupViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onContinue = { navController.navigate(Route.SelectParticipants) }
                )
            }
            composable<Route.SelectParticipants> {
                SelectParticipantsScreen(
                    viewModel = newGroupViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onFinish = {
                        navController.navigate(Route.Groups) {
                            popUpTo(Route.Groups) { inclusive = true }
                        }
                    }
                )
            }
            composable<Route.GroupDetail> { backStackEntry ->
                val route: Route.GroupDetail = backStackEntry.toRoute()
                GroupDetailScreen(
                    groupId = route.groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddExpense = { 
                        navController.navigate(Route.AddExpense(route.groupId)) 
                    },
                    onNavigateToSettings = { 
                        // Using SplitDetail route for settings as mock
                        navController.navigate(Route.SplitDetail(route.groupId)) 
                    },
                    onNavigateToExpenseDetail = { expenseId ->
                        navController.navigate(Route.ExpenseDetail(route.groupId, expenseId))
                    }
                )
            }
            composable<Route.AddExpense> { backStackEntry ->
                val route: Route.AddExpense = backStackEntry.toRoute()
                AddExpenseScreen(
                    groupId = route.groupId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Route.ExpenseDetail> { backStackEntry ->
                val route: Route.ExpenseDetail = backStackEntry.toRoute()
                ExpenseDetailScreen(
                    groupId = route.groupId,
                    expenseId = route.expenseId,
                    onNavigateBack = { navController.popBackStack() },
                    onViewReceipt = { /* Open full receipt */ }
                )
            }
            composable<Route.SplitDetail> { backStackEntry ->
                val route: Route.SplitDetail = backStackEntry.toRoute()
                GroupSettingsScreen(
                    groupId = route.groupId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Route.Account> {
                AccountScreen(
                    onNavigateToUpgrade = { navController.navigate(Route.UpgradePlan) },
                    onNavigateToPersonalInfo = { navController.navigate(Route.PersonalInfo) },
                    onNavigateToNotification = { navController.navigate(Route.NotificationSettings) },
                    onNavigateToSecurity = { navController.navigate(Route.SecuritySettings) },
                    onNavigateToPaymentMethods = { navController.navigate(Route.PaymentMethods) },
                    onNavigateToBilling = { navController.navigate(Route.BillingSubscriptions) },
                    onNavigateToLinkedAccounts = { navController.navigate(Route.LinkedAccounts) },
                    onNavigateToAppearance = { navController.navigate(Route.AppAppearance) },
                    onNavigateToDataAnalytics = { navController.navigate(Route.DataAnalytics) },
                    onNavigateToHelpSupport = { navController.navigate(Route.HelpSupport) }
                )
            }
            composable<Route.PersonalInfo> {
                PersonalInfoScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.NotificationSettings> {
                NotificationSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.SecuritySettings> {
                SecuritySettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.BillingSubscriptions> {
                BillingSubscriptionsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.LinkedAccounts> {
                LinkedAccountsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.DataAnalytics> {
                DataAnalyticsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.AppAppearance> {
                AppAppearanceScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLanguage = { navController.navigate(Route.AppLanguage) }
                )
            }
            composable<Route.AppLanguage> {
                AppLanguageScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.HelpSupport> {
                HelpSupportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFAQ = { navController.navigate(Route.FAQ) },
                    onNavigateToContactSupport = { navController.navigate(Route.ContactSupport) },
                    onNavigateToPrivacyPolicy = { navController.navigate(Route.PrivacyPolicy) },
                    onNavigateToTermsOfService = { navController.navigate(Route.TermsOfService) }
                )
            }
            composable<Route.FAQ> {
                FAQScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.ContactSupport> {
                ContactSupportScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.PrivacyPolicy> {
                PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.TermsOfService> {
                TermsOfServiceScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.UpgradePlan> {
                UpgradePlanScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPayment = { navController.navigate(Route.PaymentMethods) }
                )
            }
            composable<Route.PaymentMethods> {
                PaymentMethodsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddPayment = { navController.navigate(Route.AddPaymentMethod) },
                    onNavigateToSuccess = { 
                        navController.navigate(Route.UpgradeSuccess) {
                            popUpTo(Route.UpgradePlan) { inclusive = true }
                        }
                    }
                )
            }
            composable<Route.AddPaymentMethod> {
                AddPaymentMethodScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.UpgradeSuccess> {
                UpgradeSuccessScreen(
                    onDone = { navController.popBackStack() }
                )
            }
            composable<Route.Contact> {
                ContactScreen(
                    onVisibilityChanged = { visible -> showBottomBarOverride = visible }
                )
            }
            composable<Route.Scan> {
                ScanScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
