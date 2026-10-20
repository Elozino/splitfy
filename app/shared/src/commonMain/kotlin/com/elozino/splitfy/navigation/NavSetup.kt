package com.elozino.splitfy.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.elozino.splitfy.FeatureFlags
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.SessionState
import com.elozino.splitfy.screens.account.add_payment_method.AddPaymentMethodScreen
import com.elozino.splitfy.screens.account.appearance.AppAppearanceScreen
import com.elozino.splitfy.screens.account.billing_subscriptions.BillingSubscriptionsScreen
import com.elozino.splitfy.screens.account.contact_support.ContactSupportScreen
import com.elozino.splitfy.screens.account.data_analytics.DataAnalyticsScreen
import com.elozino.splitfy.screens.account.faq.FAQScreen
import com.elozino.splitfy.screens.account.help.HelpSupportScreen
import com.elozino.splitfy.screens.account.language.AppLanguageScreen
import com.elozino.splitfy.screens.account.linked_accounts.LinkedAccountsScreen
import com.elozino.splitfy.screens.account.notification_settings.NotificationSettingsScreen
import com.elozino.splitfy.screens.account.overview.AccountScreen
import com.elozino.splitfy.screens.account.payment_methods.PaymentMethodsScreen
import com.elozino.splitfy.screens.account.personal_info.PersonalInfoScreen
import com.elozino.splitfy.screens.account.privacy.PrivacyPolicyScreen
import com.elozino.splitfy.screens.account.security_settings.SecuritySettingsScreen
import com.elozino.splitfy.screens.account.terms.TermsOfServiceScreen
import com.elozino.splitfy.screens.account.upgrade_plan.UpgradePlanScreen
import com.elozino.splitfy.screens.account.upgrade_success.UpgradeSuccessScreen
import com.elozino.splitfy.screens.activity.list.ActivityScreen
import com.elozino.splitfy.screens.auth.forgot_password.ForgotPasswordScreen
import com.elozino.splitfy.screens.auth.landing.AuthScreen
import com.elozino.splitfy.screens.auth.login.LoginScreen
import com.elozino.splitfy.screens.auth.new_password.NewPasswordScreen
import com.elozino.splitfy.screens.auth.otp.OtpScreen
import com.elozino.splitfy.screens.auth.otp.VerifyEmailScreen
import com.elozino.splitfy.screens.auth.shared.AuthViewModel
import com.elozino.splitfy.screens.auth.signup.SignUpScreen
import com.elozino.splitfy.screens.contacts.main.ContactScreen
import com.elozino.splitfy.screens.expenses.add.AddExpenseScreen
import com.elozino.splitfy.screens.groups.create.details.NewGroupScreen
import com.elozino.splitfy.screens.groups.create.participants.SelectParticipantsScreen
import com.elozino.splitfy.screens.groups.create.shared.NewGroupViewModel
import com.elozino.splitfy.screens.groups.detail.expense.ExpenseDetailScreen
import com.elozino.splitfy.screens.groups.detail.main.GroupDetailScreen
import com.elozino.splitfy.screens.groups.list.GroupsScreen
import com.elozino.splitfy.screens.groups.settings.GroupSettingsScreen
import com.elozino.splitfy.screens.settlements.SettleUpScreen
import com.elozino.splitfy.screens.home.main.HomeScreen
import com.elozino.splitfy.screens.notifications.list.NotificationScreen
import com.elozino.splitfy.screens.onboarding.main.OnboardingScreen
import com.elozino.splitfy.screens.payments.pay.PayScreenWrapper
import com.elozino.splitfy.screens.payments.request.RequestScreenWrapper
import com.elozino.splitfy.screens.scan.pay.ScanScreen
import com.elozino.splitfy.screens.transactions.history.TransactionHistoryScreen
import com.elozino.splitfy.screens.wallet.top_up.TopUpScreen
import com.elozino.splitfy.screens.wallet.withdraw.WithdrawScreen
import com.elozino.splitfy.ui.components.MainBottomNavigation
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

    // Chosen once. Recomputing it from the live session rebuilt the graph the moment
    // sign-up or login succeeded, resetting the back stack to Home and discarding
    // whatever screen the flow had just navigated to (such as email verification).
    val startDestination = remember { if (isAuthenticated) Route.Home else Route.Onboarding }

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

    val inviteToken by com.elozino.splitfy.invites.InviteLinks.token.collectAsStateWithLifecycle()
    var inviteWaitingForAuth by androidx.compose.runtime.saveable.rememberSaveable(inviteToken) { mutableStateOf(false) }
    LaunchedEffect(inviteToken) { if (inviteToken == null) inviteWaitingForAuth = false }
    // Wait until the existing authentication flow reaches Home; sign-up must finish verification first.
    val inviteAuthFinished = isAuthenticated && currentDestination?.hasRoute(Route.Home::class) == true
    if (inviteToken != null && (!inviteWaitingForAuth || inviteAuthFinished)) {
        com.elozino.splitfy.invites.InvitePreviewDialog(
            token = inviteToken!!,
            signedIn = isAuthenticated,
            onSignIn = { inviteWaitingForAuth = true; navController.navigate(Route.Auth) },
            onJoined = { id ->
                inviteWaitingForAuth = false
                navController.navigate(Route.GroupDetail(id))
            },
        )
    }

    // A tapped push notification opens its group once someone is signed in.
    if (isAuthenticated) com.elozino.splitfy.push.RequestPushPermission()
    val pushGroupId by com.elozino.splitfy.push.PushNotifications.pendingGroupId.collectAsStateWithLifecycle()
    // Not while the sign-in or email-verification flow is still on screen.
    val pushCanNavigate = isAuthenticated && currentDestination != null &&
        listOf(Route.Onboarding::class, Route.Auth::class, Route.Login::class, Route.Signup::class, Route.VerifyEmail::class)
            .none { currentDestination.hasRoute(it) }
    LaunchedEffect(pushGroupId, pushCanNavigate) {
        val groupId = pushGroupId ?: return@LaunchedEffect
        if (!pushCanNavigate) return@LaunchedEffect
        com.elozino.splitfy.push.PushNotifications.consumePendingGroup()
        navController.navigate(Route.GroupDetail(groupId))
    }

    val showBottomBar =
        showBottomBarOverride && (currentDestination?.hasRoute(Route.Home::class) == true || currentDestination?.hasRoute(
            Route.Groups::class
        ) == true || currentDestination?.hasRoute(Route.Contact::class) == true || currentDestination?.hasRoute(Route.Account::class) == true)

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
                MainBottomNavigation(selectedTab = selectedTab, onTabSelected = { index ->
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
                }, onCenterClick = {
                    navController.navigate(if (FeatureFlags.WALLET) Route.Scan else Route.NewGroup)
                }, centerIcon = if (FeatureFlags.WALLET) Icons.Default.QrCodeScanner else Icons.Default.Add,
                    centerContentDescription = if (FeatureFlags.WALLET) "Scan" else "New group")
            }
        }) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
                // Reserve the bar's height so the last row of a tab screen — the final
                // favourite, the last group — is not hidden underneath it. Only while the
                // bar is actually shown: every other destination draws to the bottom edge
                // and manages its own insets, and padding those would leave a dead strip.
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            composable<Route.Onboarding> {
                OnboardingScreen(
                    onNavigateToAuth = {
                        navController.navigate(Route.Auth)
                    })
            }
            composable<Route.Auth> {
                AuthScreen(
                    viewModel = authViewModel,
                    onNavigateToRegister = { navController.navigate(Route.Signup) },
                    onNavigateToLogin = { navController.navigate(Route.Login) },
                    onSignedIn = {
                        navController.navigate(Route.Home) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    })
            }
            composable<Route.Signup> {
                SignUpScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = { navController.navigate(Route.Login) },
                    onSignUpSuccess = {
                        authViewModel.resetOtpTimer()
                        navController.navigate(Route.VerifyEmail) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    })
            }
            composable<Route.VerifyEmail> {
                val signedInEmail = (sessionState as? SessionState.SignedIn)?.user?.email
                VerifyEmailScreen(
                    viewModel = authViewModel,
                    email = signedInEmail ?: authViewModel.state.value.email,
                    onDone = {
                        navController.navigate(Route.Home) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    })
            }
            composable<Route.Login> {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSignup = { navController.navigate(Route.Signup) },
                    onNavigateToForgotPassword = { navController.navigate(Route.ForgotPassword) },
                    onLoginSuccess = { navController.navigate(Route.Home) })
            }
            composable<Route.ForgotPassword> {
                ForgotPasswordScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOtpSent = { navController.navigate(Route.Otp) })
            }
            composable<Route.Otp> {
                OtpScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOtpVerified = { navController.navigate(Route.NewPassword) })
            }
            composable<Route.NewPassword> {
                NewPasswordScreen(
                    viewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPasswordChanged = { navController.navigate(Route.Login) })
            }
            composable<Route.Home> {
                HomeScreen(
                    onNavigateToNotification = { navController.navigate(Route.Notification) },
                    onNavigateToActivity = { navController.navigate(Route.Activity) },
                    onNavigateToPay = { navController.navigate(Route.Pay) },
                    onNavigateToRequest = { navController.navigate(Route.Request) },
                    onNavigateToTopUp = { navController.navigate(Route.TopUp) },
                    onNavigateToWithdraw = { navController.navigate(Route.Withdraw) },
                    onNavigateToTransactionHistory = { navController.navigate(Route.TransactionHistory) },
                    onNavigateToNewGroup = { navController.navigate(Route.NewGroup) },
                    onNavigateToGroups = {
                        navController.navigate(Route.Groups) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToGroupDetail = { groupId -> navController.navigate(Route.GroupDetail(groupId)) },
                    onNavigateToVerifyEmail = {
                        authViewModel.resetOtpTimer()
                        authViewModel.resendVerification()
                        navController.navigate(Route.VerifyEmail)
                    })
            }
            composable<Route.Pay> {
                PayScreenWrapper(
                    onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.Request> {
                RequestScreenWrapper(
                    onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.TopUp> {
                TopUpScreen(
                    onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.Withdraw> {
                WithdrawScreen(
                    onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.TransactionHistory> {
                TransactionHistoryScreen(
                    onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.Notification> {
                NotificationScreen(
                    onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.Activity> {
                ActivityScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPay = { navController.navigate(Route.Pay) }.takeIf { FeatureFlags.WALLET },
                    onNavigateToRequest = { navController.navigate(Route.Request) }.takeIf { FeatureFlags.WALLET },
                    onNavigateToSettleUp = { groupId -> navController.navigate(Route.SettleUp(groupId)) })
            }
            composable<Route.Groups> {
                GroupsScreen(
                    onNavigateToNewGroup = { navController.navigate(Route.NewGroup) },
                    onNavigateToGroupDetail = { groupId ->
                        navController.navigate(Route.GroupDetail(groupId))
                    })
            }
            composable<Route.NewGroup> {
                NewGroupScreen(
                    viewModel = newGroupViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onContinue = { navController.navigate(Route.SelectParticipants) })
            }
            composable<Route.SelectParticipants> {
                SelectParticipantsScreen(
                    viewModel = newGroupViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onFinish = {
                        navController.navigate(Route.Groups) {
                            popUpTo(Route.Groups) { inclusive = true }
                        }
                    })
            }
            composable<Route.GroupDetail> { backStackEntry ->
                val route: Route.GroupDetail = backStackEntry.toRoute()
                GroupDetailScreen(
                    groupId = route.groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddExpense = {
                        navController.navigate(Route.AddExpense(route.groupId))
                    },
                    onNavigateToSettleUp = {
                        navController.navigate(Route.SettleUp(route.groupId))
                    },
                    onNavigateToInvite = { navController.navigate(Route.ShareInvite(route.groupId)) },
                    onNavigateToSettings = {
                        navController.navigate(Route.GroupSettings(route.groupId))
                    },
                    onNavigateToExpenseDetail = { expenseId ->
                        navController.navigate(Route.ExpenseDetail(route.groupId, expenseId))
                    })
            }
            composable<Route.ShareInvite> { backStackEntry ->
                val route: Route.ShareInvite = backStackEntry.toRoute()
                com.elozino.splitfy.invites.ShareInviteScreen(route.groupId) { navController.popBackStack() }
            }
            composable<Route.SettleUp> { backStackEntry ->
                val route: Route.SettleUp = backStackEntry.toRoute()
                SettleUpScreen(groupId = route.groupId, onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.AddExpense> { backStackEntry ->
                val route: Route.AddExpense = backStackEntry.toRoute()
                AddExpenseScreen(
                    groupId = route.groupId, onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.ExpenseDetail> { backStackEntry ->
                val route: Route.ExpenseDetail = backStackEntry.toRoute()
                ExpenseDetailScreen(
                    groupId = route.groupId,
                    expenseId = route.expenseId,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<Route.GroupSettings> { backStackEntry ->
                val route: Route.GroupSettings = backStackEntry.toRoute()
                GroupSettingsScreen(
                    groupId = route.groupId, onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.Account> {
                AccountScreen(
                    // Paid plans and payment methods depend on the wallet's payment provider.
                    onNavigateToUpgrade = { navController.navigate(Route.UpgradePlan) }.takeIf { FeatureFlags.WALLET },
                    onNavigateToPersonalInfo = { navController.navigate(Route.PersonalInfo) },
                    onNavigateToNotification = { navController.navigate(Route.NotificationSettings) },
                    onNavigateToSecurity = { navController.navigate(Route.SecuritySettings) },
                    onNavigateToPaymentMethods = { navController.navigate(Route.PaymentMethods) }
                        .takeIf { FeatureFlags.WALLET },
                    onNavigateToBilling = { navController.navigate(Route.BillingSubscriptions) }
                        .takeIf { FeatureFlags.WALLET },
                    onNavigateToLinkedAccounts = { navController.navigate(Route.LinkedAccounts) },
                    onNavigateToAppearance = { navController.navigate(Route.AppAppearance) },
                    onNavigateToDataAnalytics = { navController.navigate(Route.DataAnalytics) },
                    onNavigateToHelpSupport = { navController.navigate(Route.HelpSupport) })
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
                    onNavigateToLanguage = { navController.navigate(Route.AppLanguage) })
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
                    onNavigateToTermsOfService = { navController.navigate(Route.TermsOfService) })
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
                    onNavigateToPayment = { navController.navigate(Route.PaymentMethods) })
            }
            composable<Route.PaymentMethods> {
                PaymentMethodsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddPayment = { navController.navigate(Route.AddPaymentMethod) },
                    onNavigateToSuccess = {
                        navController.navigate(Route.UpgradeSuccess) {
                            popUpTo(Route.UpgradePlan) { inclusive = true }
                        }
                    })
            }
            composable<Route.AddPaymentMethod> {
                AddPaymentMethodScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Route.UpgradeSuccess> {
                UpgradeSuccessScreen(
                    onDone = { navController.popBackStack() })
            }
            composable<Route.Contact> {
                ContactScreen(
                    onVisibilityChanged = { visible -> showBottomBarOverride = visible })
            }
            composable<Route.Scan> {
                ScanScreen(
                    onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
