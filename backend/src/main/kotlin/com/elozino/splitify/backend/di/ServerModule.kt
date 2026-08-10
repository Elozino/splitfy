package com.elozino.splitify.backend.di

import com.elozino.splitify.backend.repositories.ExpenseRepository
import com.elozino.splitify.backend.repositories.GroupRepository
import com.elozino.splitify.backend.repositories.PasswordResetRepository
import com.elozino.splitify.backend.repositories.PaymentMethodRepository
import com.elozino.splitify.backend.repositories.RefreshTokenRepository
import com.elozino.splitify.backend.repositories.SettlementRepository
import com.elozino.splitify.backend.repositories.TransferRepository
import com.elozino.splitify.backend.repositories.UserRepository
import com.elozino.splitify.backend.repositories.WalletRepository
import com.elozino.splitify.backend.services.ActivityService
import com.elozino.splitify.backend.services.AuthService
import com.elozino.splitify.backend.services.ContactFavoriteRepository
import com.elozino.splitify.backend.services.ContactService
import com.elozino.splitify.backend.services.ExpenseService
import com.elozino.splitify.backend.services.GroupService
import com.elozino.splitify.backend.services.JwtService
import com.elozino.splitify.backend.services.LoggingOtpNotifier
import com.elozino.splitify.backend.services.NotificationRepository
import com.elozino.splitify.backend.services.NotificationService
import com.elozino.splitify.backend.services.OtpNotifier
import com.elozino.splitify.backend.services.SettlementService
import com.elozino.splitify.backend.services.TransferService
import com.elozino.splitify.backend.services.UserService
import com.elozino.splitify.backend.services.WalletService
import com.elozino.splitify.backend.utils.AppConfig
import com.elozino.splitify.backend.utils.PasswordHasher
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
