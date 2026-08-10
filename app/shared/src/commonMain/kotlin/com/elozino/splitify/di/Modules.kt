package com.elozino.splitify.di

import com.elozino.splitify.CardTokenizer
import com.elozino.splitify.DevCardTokenizer
import com.elozino.splitify.network.ApiCaller
import com.elozino.splitify.network.ApiConfig
import com.elozino.splitify.network.createHttpClientEngine
import com.elozino.splitify.network.HttpClientFactory
import com.elozino.splitify.repositories.ActivityRepository
import com.elozino.splitify.repositories.ActivityRepositoryImpl
import com.elozino.splitify.repositories.AuthRepository
import com.elozino.splitify.repositories.AuthRepositoryImpl
import com.elozino.splitify.repositories.ContactRepository
import com.elozino.splitify.repositories.ContactRepositoryImpl
import com.elozino.splitify.repositories.ExpenseRepository
import com.elozino.splitify.repositories.ExpenseRepositoryImpl
import com.elozino.splitify.repositories.GroupRepository
import com.elozino.splitify.repositories.GroupRepositoryImpl
import com.elozino.splitify.repositories.NotificationRepository
import com.elozino.splitify.repositories.NotificationRepositoryImpl
import com.elozino.splitify.repositories.SettlementRepository
import com.elozino.splitify.repositories.SettlementRepositoryImpl
import com.elozino.splitify.repositories.TransferRepository
import com.elozino.splitify.repositories.TransferRepositoryImpl
import com.elozino.splitify.repositories.UserRepository
import com.elozino.splitify.repositories.UserRepositoryImpl
import com.elozino.splitify.repositories.WalletRepository
import com.elozino.splitify.repositories.WalletRepositoryImpl
import com.elozino.splitify.screens.AccountViewModel
import com.elozino.splitify.screens.ActivityViewModel
import com.elozino.splitify.screens.AddExpenseViewModel
import com.elozino.splitify.screens.AuthViewModel
import com.elozino.splitify.screens.ContactsViewModel
import com.elozino.splitify.screens.GroupDetailViewModel
import com.elozino.splitify.screens.GroupSettingsViewModel
import com.elozino.splitify.screens.GroupsViewModel
import com.elozino.splitify.screens.HomeViewModel
import com.elozino.splitify.screens.NewGroupViewModel
import com.elozino.splitify.screens.NotificationViewModel
import com.elozino.splitify.screens.TransactionViewModel
import com.elozino.splitify.screens.WalletViewModel
import com.elozino.splitify.SessionManager
import com.elozino.splitify.storage.SessionStorage
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
fun dataModule(isDebug: Boolean): Module = module {

    single { ApiConfig.forBuild(isDebug) }
    single { SessionStorage(get()) }
    single { SessionManager(get()) }
    single { ApiCaller(get()) }

    single<HttpClient> {
        HttpClientFactory(
            engine = createHttpClientEngine(),
            config = get(),
            sessionManager = get(),
        ).create()
    }

    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<GroupRepository> { GroupRepositoryImpl(get(), get()) }
    single<ExpenseRepository> { ExpenseRepositoryImpl(get(), get()) }
    single<SettlementRepository> { SettlementRepositoryImpl(get(), get()) }
    single<WalletRepository> { WalletRepositoryImpl(get(), get()) }
    single<TransferRepository> { TransferRepositoryImpl(get(), get()) }
    single<ActivityRepository> { ActivityRepositoryImpl(get(), get()) }
    single<NotificationRepository> { NotificationRepositoryImpl(get(), get()) }
    single<ContactRepository> { ContactRepositoryImpl(get(), get()) }

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
    factory { NewGroupViewModel(get(), get()) }
    factory { (groupId: String) -> GroupDetailViewModel(groupId, get(), get(), get()) }
    factory { (groupId: String) -> AddExpenseViewModel(groupId, get(), get(), get()) }
    factory { (groupId: String) -> GroupSettingsViewModel(groupId, get(), get()) }
    factory { WalletViewModel(get()) }
    factory { TransactionViewModel(get(), get(), get()) }
    factory { ActivityViewModel(get(), get()) }
    factory { NotificationViewModel(get()) }
    factory { ContactsViewModel(get(), get()) }
    factory { AccountViewModel(get(), get(), get(), get(), get()) }
}

/**
 * Entry point for both platforms.
 *
 * Android passes `BuildConfig.DEBUG` and the application context; iOS passes
 * `Platform.isDebugBinary`.
 */
fun initKoin(
    isDebug: Boolean,
    platformConfig: (KoinApplication.() -> Unit)? = null,
): KoinApplication = startKoin {
    platformConfig?.invoke(this)
    modules(platformModule(), dataModule(isDebug), viewModelModule)
}

/** Convenience for `koinViewModel { parametersOf(groupId) }` call sites. */
fun groupParams(groupId: String) = parametersOf(groupId)
