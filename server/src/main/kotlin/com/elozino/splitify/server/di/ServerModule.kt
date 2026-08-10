package com.elozino.splitify.server.di

import com.elozino.splitify.server.common.PasswordHasher
import com.elozino.splitify.server.config.AppConfig
import com.elozino.splitify.server.feature.auth.AuthService
import com.elozino.splitify.server.feature.auth.JwtService
import com.elozino.splitify.server.feature.auth.LoggingOtpNotifier
import com.elozino.splitify.server.feature.auth.OtpNotifier
import com.elozino.splitify.server.feature.auth.PasswordResetRepository
import com.elozino.splitify.server.feature.auth.RefreshTokenRepository
import com.elozino.splitify.server.feature.activity.ActivityService
import com.elozino.splitify.server.feature.contact.ContactFavoriteRepository
import com.elozino.splitify.server.feature.contact.ContactService
import com.elozino.splitify.server.feature.expense.ExpenseRepository
import com.elozino.splitify.server.feature.expense.ExpenseService
import com.elozino.splitify.server.feature.group.GroupRepository
import com.elozino.splitify.server.feature.notification.NotificationRepository
import com.elozino.splitify.server.feature.notification.NotificationService
import com.elozino.splitify.server.feature.group.GroupService
import com.elozino.splitify.server.feature.settlement.SettlementRepository
import com.elozino.splitify.server.feature.settlement.SettlementService
import com.elozino.splitify.server.feature.user.UserRepository
import com.elozino.splitify.server.feature.transfer.TransferRepository
import com.elozino.splitify.server.feature.transfer.TransferService
import com.elozino.splitify.server.feature.user.UserService
import com.elozino.splitify.server.feature.wallet.PaymentMethodRepository
import com.elozino.splitify.server.feature.wallet.WalletRepository
import com.elozino.splitify.server.feature.wallet.WalletService
import org.koin.dsl.module

/**
 * Wiring for the whole server.
 *
 * Everything is constructor-injected and stateless, so swapping a repository for a
 * fake in a test is a one-line module override rather than a refactor.
 */
fun serverModule(config: AppConfig) = module {

    single { config }
    single { config.jwt }
    single { config.database }

    single { JwtService(config.jwt) }
    single { PasswordHasher(config.security.bcryptCost) }
    single<OtpNotifier> { LoggingOtpNotifier(config.environment) }

    // Repositories
    single { UserRepository() }
    single { RefreshTokenRepository() }
    single { PasswordResetRepository() }
    single { GroupRepository() }
    single { ExpenseRepository() }
    single { SettlementRepository() }
    single { WalletRepository() }
    single { PaymentMethodRepository() }
    single { TransferRepository() }
    single { NotificationRepository() }
    single { ContactFavoriteRepository() }

    // Services
    single { AuthService(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { UserService(get()) }
    single { GroupService(get(), get(), get(), get()) }
    single { ExpenseService(get(), get()) }
    single { SettlementService(get(), get()) }
    single { NotificationService(get()) }
    single { WalletService(get(), get(), get(), get()) }
    single { TransferService(get(), get(), get(), get()) }
    single { ActivityService(get(), get(), get(), get()) }
    single { ContactService(get(), get(), get(), get(), get()) }
}
