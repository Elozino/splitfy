package com.elozino.splitfy.backend.di

import com.elozino.splitfy.backend.repositories.ExpenseRepository
import com.elozino.splitfy.backend.repositories.GroupRepository
import com.elozino.splitfy.backend.repositories.PasswordResetRepository
import com.elozino.splitfy.backend.repositories.PaymentMethodRepository
import com.elozino.splitfy.backend.repositories.RefreshTokenRepository
import com.elozino.splitfy.backend.repositories.SettlementRepository
import com.elozino.splitfy.backend.repositories.TransferRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.repositories.WalletRepository
import com.elozino.splitfy.backend.services.ActivityService
import com.elozino.splitfy.backend.services.AuthService
import com.elozino.splitfy.backend.services.ContactFavoriteRepository
import com.elozino.splitfy.backend.services.ContactService
import com.elozino.splitfy.backend.services.ExpenseService
import com.elozino.splitfy.backend.services.GroupService
import com.elozino.splitfy.backend.services.JwtService
import com.elozino.splitfy.backend.services.LoggingOtpNotifier
import com.elozino.splitfy.backend.services.NotificationRepository
import com.elozino.splitfy.backend.services.NotificationService
import com.elozino.splitfy.backend.services.OtpNotifier
import com.elozino.splitfy.backend.services.SettlementService
import com.elozino.splitfy.backend.services.TransferService
import com.elozino.splitfy.backend.services.UserService
import com.elozino.splitfy.backend.services.WalletService
import com.elozino.splitfy.backend.utils.AppConfig
import com.elozino.splitfy.backend.utils.PasswordHasher
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
