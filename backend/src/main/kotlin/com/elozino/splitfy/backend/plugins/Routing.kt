package com.elozino.splitfy.backend.plugins

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.backend.routes.activityRoutes
import com.elozino.splitfy.backend.routes.authRoutes
import com.elozino.splitfy.backend.routes.contactRoutes
import com.elozino.splitfy.backend.routes.expenseRoutes
import com.elozino.splitfy.backend.routes.groupRoutes
import com.elozino.splitfy.backend.routes.healthRoutes
import com.elozino.splitfy.backend.routes.notificationRoutes
import com.elozino.splitfy.backend.routes.openApiRoutes
import com.elozino.splitfy.backend.routes.pushRoutes
import com.elozino.splitfy.backend.routes.settlementRoutes
import com.elozino.splitfy.backend.routes.transferRoutes
import com.elozino.splitfy.backend.routes.userRoutes
import com.elozino.splitfy.backend.routes.walletRoutes
import com.elozino.splitfy.backend.services.AccountDeletionService
import com.elozino.splitfy.backend.services.ActivityService
import com.elozino.splitfy.backend.utils.AppConfig
import com.elozino.splitfy.backend.services.AuthService
import com.elozino.splitfy.backend.services.SocialAuthService
import com.elozino.splitfy.backend.services.InviteService
import com.elozino.splitfy.backend.routes.inviteRoutes
import com.elozino.splitfy.backend.routes.publicInviteRoutes
import com.elozino.splitfy.backend.services.ContactService
import com.elozino.splitfy.backend.services.ExpenseService
import com.elozino.splitfy.backend.services.GroupService
import com.elozino.splitfy.backend.services.JwtService
import com.elozino.splitfy.backend.services.NotificationService
import com.elozino.splitfy.backend.services.SettlementService
import com.elozino.splitfy.backend.services.TransferService
import com.elozino.splitfy.backend.services.UserService
import com.elozino.splitfy.backend.services.WalletService
import com.elozino.splitfy.backend.services.UploadService
import com.elozino.splitfy.backend.routes.uploadRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.jetbrains.exposed.sql.Database
import org.koin.ktor.ext.inject

/**
 * Route registration.
 *
 * Authentication is applied once, around everything outside the auth feature, so a
 * new endpoint is protected by default — forgetting an `authenticate {}` block
 * cannot silently expose data.
 */
fun Application.configureRouting(database: Database, config: AppConfig, metrics: PrometheusMeterRegistry) {
    val features = config.features
    val authService by inject<AuthService>()
    val socialAuthService by inject<SocialAuthService>()
    val inviteService by inject<InviteService>()
    val userService by inject<UserService>()
    val accountDeletionService by inject<AccountDeletionService>()
    val groupService by inject<GroupService>()
    val expenseService by inject<ExpenseService>()
    val settlementService by inject<SettlementService>()
    val walletService by inject<WalletService>()
    val transferService by inject<TransferService>()
    val activityService by inject<ActivityService>()
    val notificationService by inject<NotificationService>()
    val contactService by inject<ContactService>()
    val uploadService by inject<UploadService>()

    routing {
        swaggerUI(path = ApiRoutes.System.OPENAPI, swaggerFile = "openapi/documentation.yaml")
        openApiRoutes()

        healthRoutes(database)
        metricsRoute(metrics, config.observability)
        authRoutes(authService, socialAuthService)
        rateLimit(RateLimitScope.AUTH_GENERAL.scopeName) {
            publicInviteRoutes(inviteService, config.links)
        }

        authenticate(JwtService.AUTH_JWT) {
            rateLimit(RateLimitScope.API.scopeName) {
                userRoutes(userService, accountDeletionService)
                groupRoutes(groupService)
                expenseRoutes(expenseService)
                settlementRoutes(settlementService)
                inviteRoutes(inviteService)
                // Unregistered rather than guarded, so a disabled wallet is a plain 404.
                if (features.wallet) {
                    walletRoutes(walletService)
                    transferRoutes(transferService)
                }
                activityRoutes(activityService)
                notificationRoutes(notificationService)
                pushRoutes(notificationService)
                contactRoutes(contactService)
                uploadRoutes(uploadService)
            }
        }
    }
}
