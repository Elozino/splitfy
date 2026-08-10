package com.elozino.splitify.backend.plugins

import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.backend.routes.activityRoutes
import com.elozino.splitify.backend.routes.authRoutes
import com.elozino.splitify.backend.routes.contactRoutes
import com.elozino.splitify.backend.routes.expenseRoutes
import com.elozino.splitify.backend.routes.groupRoutes
import com.elozino.splitify.backend.routes.healthRoutes
import com.elozino.splitify.backend.routes.notificationRoutes
import com.elozino.splitify.backend.routes.settlementRoutes
import com.elozino.splitify.backend.routes.transferRoutes
import com.elozino.splitify.backend.routes.userRoutes
import com.elozino.splitify.backend.routes.walletRoutes
import com.elozino.splitify.backend.services.ActivityService
import com.elozino.splitify.backend.services.AuthService
import com.elozino.splitify.backend.services.ContactService
import com.elozino.splitify.backend.services.ExpenseService
import com.elozino.splitify.backend.services.GroupService
import com.elozino.splitify.backend.services.JwtService
import com.elozino.splitify.backend.services.NotificationService
import com.elozino.splitify.backend.services.SettlementService
import com.elozino.splitify.backend.services.TransferService
import com.elozino.splitify.backend.services.UserService
import com.elozino.splitify.backend.services.WalletService
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.Database
import org.koin.ktor.ext.inject

/**
 * Route registration.
 *
 * Authentication is applied once, around everything outside the auth feature, so a
 * new endpoint is protected by default — forgetting an `authenticate {}` block
 * cannot silently expose data.
 */
fun Application.configureRouting(database: Database) {
    val authService by inject<AuthService>()
    val userService by inject<UserService>()
    val groupService by inject<GroupService>()
    val expenseService by inject<ExpenseService>()
    val settlementService by inject<SettlementService>()
    val walletService by inject<WalletService>()
    val transferService by inject<TransferService>()
    val activityService by inject<ActivityService>()
    val notificationService by inject<NotificationService>()
    val contactService by inject<ContactService>()

    routing {
        swaggerUI(path = ApiRoutes.System.OPENAPI, swaggerFile = "openapi/documentation.yaml")

        healthRoutes(database)
        authRoutes(authService)

        authenticate(JwtService.AUTH_JWT) {
            rateLimit(RateLimitScope.API.scopeName) {
                userRoutes(userService)
                groupRoutes(groupService)
                expenseRoutes(expenseService)
                settlementRoutes(settlementService)
                walletRoutes(walletService)
                transferRoutes(transferService)
                activityRoutes(activityService)
                notificationRoutes(notificationService)
                contactRoutes(contactService)
            }
        }
    }
}
