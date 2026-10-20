package com.elozino.splitfy.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Route(val name: String, val isProtected: Boolean) {

    @Serializable
    object Onboarding : Route("onboarding", false)

    @Serializable
    object Auth : Route("auth", false)

    @Serializable
    object Login : Route("login", false)

    @Serializable
    object Signup : Route("signup", false)

    @Serializable
    object ForgotPassword : Route("forgot_password", false)

    @Serializable
    object NewPassword : Route("new_password", false)

    @Serializable
    object Otp : Route("otp", false)

    @Serializable
    object VerifyEmail : Route("verify_email", true)

    @Serializable
    object Home : Route("home", true)

    @Serializable
    object Groups : Route("groups", true)

    @Serializable
    object Account : Route("profile", true)

    @Serializable
    object Contact : Route("contact", true)

    @Serializable
    object Notification : Route("notification", true)

    @Serializable
    object Activity : Route("activity", true)

    @Serializable
    object Pay : Route("pay", true)

    @Serializable
    object Request : Route("request", true)

    @Serializable
    object TopUp : Route("top_up", true)

    @Serializable
    object Withdraw : Route("withdraw", true)

    @Serializable
    object TransactionHistory : Route("transaction_history", true)

    @Serializable
    object NewGroup : Route("new_group", true)

    @Serializable
    object SelectParticipants : Route("select_participants", true)

    @Serializable
    data class GroupDetail(val groupId: String) : Route("group_detail", true)

    @Serializable
    data class AddExpense(val groupId: String) : Route("add_expense", true)

    @Serializable
    data class ExpenseDetail(val groupId: String, val expenseId: String) : Route("expense_detail", true)

    @Serializable
    data class SettleUp(val groupId: String) : Route("settle_up", true)

    @Serializable
    data class GroupSettings(val groupId: String) : Route("group_settings", true)

    @Serializable
    data class ShareInvite(val groupId: String) : Route("share_invite", true)

    @Serializable
    object UpgradePlan : Route("upgrade_plan", true)

    @Serializable
    object PaymentMethods : Route("payment_methods", true)

    @Serializable
    object AddPaymentMethod : Route("add_payment_method", true)

    @Serializable
    object UpgradeSuccess : Route("upgrade_success", true)

    @Serializable
    object PersonalInfo : Route("personal_info", true)

    @Serializable
    object NotificationSettings : Route("notification_settings", true)

    @Serializable
    object SecuritySettings : Route("security_settings", true)

    @Serializable
    object BillingSubscriptions : Route("billing_subscriptions", true)

    @Serializable
    object LinkedAccounts : Route("linked_accounts", true)

    @Serializable
    object DataAnalytics : Route("data_analytics", true)

    @Serializable
    object AppAppearance : Route("app_appearance", true)

    @Serializable
    object AppLanguage : Route("app_language", true)

    @Serializable
    object HelpSupport : Route("help_support", true)

    @Serializable
    object FAQ : Route("faq", true)

    @Serializable
    object ContactSupport : Route("contact_support", true)

    @Serializable
    object PrivacyPolicy : Route("privacy_policy", true)

    @Serializable
    object TermsOfService : Route("terms_of_service", true)

    @Serializable
    object Scan : Route("scan", true)
}
