package com.elozino.splitify.core.di

import com.elozino.splitify.core.data.network.ApiCaller
import com.elozino.splitify.core.data.network.ApiConfig
import com.elozino.splitify.core.data.network.HttpClientFactory
import com.elozino.splitify.core.data.network.createHttpClientEngine
import com.elozino.splitify.core.data.payment.CardTokenizer
import com.elozino.splitify.core.data.payment.DevCardTokenizer
import com.elozino.splitify.core.data.repository.ActivityRepositoryImpl
import com.elozino.splitify.core.data.repository.AuthRepositoryImpl
import com.elozino.splitify.core.data.repository.ContactRepositoryImpl
import com.elozino.splitify.core.data.repository.NotificationRepositoryImpl
import com.elozino.splitify.core.data.repository.TransferRepositoryImpl
import com.elozino.splitify.core.data.repository.WalletRepositoryImpl
import com.elozino.splitify.core.data.repository.ExpenseRepositoryImpl
import com.elozino.splitify.core.data.repository.GroupRepositoryImpl
import com.elozino.splitify.core.data.repository.SettlementRepositoryImpl
import com.elozino.splitify.core.data.repository.UserRepositoryImpl
import com.elozino.splitify.core.data.storage.SessionStorage
import com.elozino.splitify.core.domain.repository.ActivityRepository
import com.elozino.splitify.core.domain.repository.AuthRepository
import com.elozino.splitify.core.domain.repository.ContactRepository
import com.elozino.splitify.core.domain.repository.NotificationRepository
import com.elozino.splitify.core.domain.repository.TransferRepository
import com.elozino.splitify.core.domain.repository.WalletRepository
import com.elozino.splitify.core.domain.repository.ExpenseRepository
import com.elozino.splitify.core.domain.repository.GroupRepository
import com.elozino.splitify.core.domain.repository.SettlementRepository
import com.elozino.splitify.core.domain.repository.UserRepository
import com.elozino.splitify.core.domain.session.SessionManager
import com.elozino.splitify.feature.account.presentation.AccountViewModel
import com.elozino.splitify.feature.activity.presentation.ActivityViewModel
import com.elozino.splitify.feature.activity.presentation.NotificationViewModel
import com.elozino.splitify.feature.auth.presentation.AuthViewModel
import com.elozino.splitify.feature.contact.presentation.ContactsViewModel
import com.elozino.splitify.feature.transaction.presentation.TransactionViewModel
import com.elozino.splitify.feature.wallet.presentation.WalletViewModel
import com.elozino.splitify.feature.home.presentation.HomeViewModel
import com.elozino.splitify.feature.split.presentation.AddExpenseViewModel
import com.elozino.splitify.feature.split.presentation.GroupDetailViewModel
import com.elozino.splitify.feature.split.presentation.GroupSettingsViewModel
import com.elozino.splitify.feature.split.presentation.GroupsViewModel
import com.elozino.splitify.feature.split.presentation.NewGroupViewModel
import io.ktor.client.HttpClient
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
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
