package com.elozino.splitfy.di

import com.elozino.splitfy.screens.account.appearance.AppearanceViewModel
import com.elozino.splitfy.preferences.ThemePreferences
import com.elozino.splitfy.social.SocialSignInConfig
import com.elozino.splitfy.social.platformGoogleClientId
import com.elozino.splitfy.CardTokenizer
import com.elozino.splitfy.DevCardTokenizer
import com.elozino.splitfy.network.ApiCaller
import com.elozino.splitfy.network.ApiConfig
import com.elozino.splitfy.network.createHttpClientEngine
import com.elozino.splitfy.network.HttpClientFactory
import com.elozino.splitfy.repositories.ActivityRepository
import com.elozino.splitfy.repositories.ActivityRepositoryImpl
import com.elozino.splitfy.repositories.AuthRepository
import com.elozino.splitfy.repositories.AuthRepositoryImpl
import com.elozino.splitfy.repositories.ContactRepository
import com.elozino.splitfy.repositories.ContactRepositoryImpl
import com.elozino.splitfy.repositories.ExpenseRepository
import com.elozino.splitfy.repositories.ExpenseRepositoryImpl
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.repositories.GroupRepositoryImpl
import com.elozino.splitfy.repositories.NotificationRepository
import com.elozino.splitfy.repositories.NotificationRepositoryImpl
import com.elozino.splitfy.repositories.SettlementRepository
import com.elozino.splitfy.repositories.SettlementRepositoryImpl
import com.elozino.splitfy.repositories.TransferRepository
import com.elozino.splitfy.repositories.TransferRepositoryImpl
import com.elozino.splitfy.repositories.UserRepository
import com.elozino.splitfy.repositories.UserRepositoryImpl
import com.elozino.splitfy.repositories.WalletRepository
import com.elozino.splitfy.repositories.WalletRepositoryImpl
import com.elozino.splitfy.screens.account.shared.AccountViewModel
import com.elozino.splitfy.screens.account.security_settings.ChangePasswordViewModel
import com.elozino.splitfy.screens.account.security_settings.DevicesViewModel
import com.elozino.splitfy.screens.account.security_settings.SecurityViewModel
import com.elozino.splitfy.screens.account.security_settings.TwoFactorSetupViewModel
import com.elozino.splitfy.repositories.SecurityRepository
import com.elozino.splitfy.repositories.SecurityRepositoryImpl
import com.elozino.splitfy.security.AppLockController
import com.elozino.splitfy.security.AppLockSettings
import com.elozino.splitfy.screens.activity.list.ActivityViewModel
import com.elozino.splitfy.screens.auth.shared.AuthViewModel
import com.elozino.splitfy.screens.contacts.main.ContactsViewModel
import com.elozino.splitfy.screens.expenses.add.AddExpenseViewModel
import com.elozino.splitfy.screens.groups.create.shared.NewGroupViewModel
import com.elozino.splitfy.screens.groups.detail.shared.GroupDetailViewModel
import com.elozino.splitfy.screens.settlements.SettleUpViewModel
import com.elozino.splitfy.screens.groups.list.GroupsViewModel
import com.elozino.splitfy.screens.groups.settings.GroupSettingsViewModel
import com.elozino.splitfy.screens.home.main.HomeViewModel
import com.elozino.splitfy.screens.notifications.list.NotificationViewModel
import com.elozino.splitfy.screens.transactions.shared.TransactionViewModel
import com.elozino.splitfy.screens.wallet.shared.WalletViewModel
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.storage.SessionStorage
import com.elozino.splitfy.uploads.UploadRepository
import com.elozino.splitfy.uploads.UploadRepositoryImpl
import com.elozino.splitfy.uploads.platformImageCompressor
import com.elozino.splitfy.uploads.storageHttpClient
import io.ktor.client.HttpClient
import org.koin.core.context.startKoin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** Platform-owned bindings: key/value storage and anything else that needs a Context. */
expect fun platformModule(): Module

/**
 * Networking, persistence and repositories.
 *
 * Repositories are bound to their interfaces so a ViewModel test can swap in a fake
 * without touching this file.
 */
fun dataModule(isDebug: Boolean, apiBaseUrl: String? = null, googleClientId: String? = null): Module = module {

    single { SocialSignInConfig(googleClientId?.takeIf { it.isNotBlank() } ?: platformGoogleClientId()) }

    single { ApiConfig.forBuild(isDebug, apiBaseUrl) }
    single { SessionStorage(get()) }
    single { ThemePreferences(get()) }
    single { SessionManager(get()) }
    single { ApiCaller(get()) }

    single<HttpClient> {
        HttpClientFactory(
            engine = createHttpClientEngine(),
            config = get(),
            sessionManager = get(),
            device = runCatching { com.elozino.splitfy.security.currentDeviceIdentity() }.getOrNull(),
        ).create()
    }

    single<AuthRepository> {
        val push = get<com.elozino.splitfy.push.PushRegistrar>()
        AuthRepositoryImpl(get(), get(), get(), beforeLogout = push::unregister)
    }
    single<com.elozino.splitfy.push.PushRepository> { com.elozino.splitfy.push.PushRepositoryImpl(get(), get()) }
    single { com.elozino.splitfy.push.PushRegistrar(get(), get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single { com.elozino.splitfy.invites.InviteRepository(get(), get()) }
    single<GroupRepository> { GroupRepositoryImpl(get(), get()) }
    single<ExpenseRepository> { ExpenseRepositoryImpl(get(), get()) }
    single<SettlementRepository> { SettlementRepositoryImpl(get(), get()) }
    single<WalletRepository> { WalletRepositoryImpl(get(), get()) }
    single<TransferRepository> { TransferRepositoryImpl(get(), get()) }
    single<ActivityRepository> { ActivityRepositoryImpl(get(), get()) }
    single<NotificationRepository> { NotificationRepositoryImpl(get(), get()) }
    single<ContactRepository> { ContactRepositoryImpl(get(), get()) }
    single<UploadRepository> { UploadRepositoryImpl(get(), get(), storageHttpClient()) }
    single<com.elozino.splitfy.repositories.AccountRepository> {
        com.elozino.splitfy.repositories.AccountRepositoryImpl(get(), get())
    }
    single { platformImageCompressor() }
    single<SecurityRepository> { SecurityRepositoryImpl(get(), get()) }
    single { AppLockSettings(get()) }
    single { AppLockController(get(), get()) }

    // Swap for the payment provider's SDK before taking real payments.
    single<CardTokenizer> { DevCardTokenizer() }
}

/**
 * ViewModels are `factory` bindings: Compose's `koinViewModel()` scopes each one to
 * its navigation entry, so a re-entered screen gets fresh state rather than a stale
 * singleton.
 */
val viewModelModule: Module = module {
    factory { AuthViewModel(get(), get()) }
    factory { HomeViewModel(get(), get(), get(), get(), get()) }
    factory { GroupsViewModel(get()) }
    factory { NewGroupViewModel(get(), get(), get()) }
    factory { (groupId: String) -> GroupDetailViewModel(groupId, get(), get(), get(), get(), get()) }
    factory { (groupId: String) -> AddExpenseViewModel(groupId, get(), get(), get(), get(), get()) }
    factory { (groupId: String) -> GroupSettingsViewModel(groupId, get(), get()) }
    factory { (groupId: String) -> SettleUpViewModel(groupId, get(), get(), get()) }
    factory { WalletViewModel(get()) }
    factory { TransactionViewModel(get(), get(), get()) }
    factory { ActivityViewModel(get(), get()) }
    factory { NotificationViewModel(get()) }
    factory { com.elozino.splitfy.screens.account.notification_settings.NotificationSettingsViewModel(get()) }
    factory { ContactsViewModel(get(), get()) }
    factory { AppearanceViewModel(get()) }
    factory { AccountViewModel(get(), get(), get(), get(), get()) }
    factory { SecurityViewModel(get(), get()) }
    factory { (hasPassword: Boolean) -> ChangePasswordViewModel(hasPassword, get()) }
    factory { DevicesViewModel(get()) }
    factory { TwoFactorSetupViewModel(get()) }
    factory {
        com.elozino.splitfy.screens.account.personal_info.PersonalInfoViewModel(
            get(), get(), get(), com.elozino.splitfy.uploads.platformAvatarCropper(), get(),
        )
    }
    factory { com.elozino.splitfy.screens.account.linked_accounts.LinkedAccountsViewModel(get()) }
    factory { com.elozino.splitfy.screens.account.data_analytics.DataAnalyticsViewModel(get(), get()) }
}

/**
 * Entry point for both platforms.
 *
 * Android passes `BuildConfig.DEBUG`, `BuildConfig.API_BASE_URL` and the application
 * context; iOS passes `Platform.isDebugBinary` and reads its own base URL out of
 * Info.plist, so it has nothing to hand over here.
 */
fun initKoin(
    isDebug: Boolean,
    apiBaseUrl: String? = null,
    googleClientId: String? = null,
    platformConfig: (KoinApplication.() -> Unit)? = null,
): KoinApplication = startKoin {
    platformConfig?.invoke(this)
    modules(platformModule(), dataModule(isDebug, apiBaseUrl, googleClientId), viewModelModule)
}.also {
    com.elozino.splitfy.invites.InviteLinks.bind(it.koin.get())
    it.koin.get<com.elozino.splitfy.push.PushRegistrar>().start()
}

/** Convenience for `koinViewModel { parametersOf(groupId) }` call sites. */
fun groupParams(groupId: String) = parametersOf(groupId)
