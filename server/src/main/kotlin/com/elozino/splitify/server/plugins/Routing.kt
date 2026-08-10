package com.elozino.splitify.server.plugins

import com.elozino.splitify.server.feature.auth.AuthService
import com.elozino.splitify.server.feature.auth.JwtService
import com.elozino.splitify.server.feature.auth.authRoutes
import com.elozino.splitify.server.feature.activity.ActivityService
import com.elozino.splitify.server.feature.contact.ContactService
import com.elozino.splitify.server.feature.expense.ExpenseService
import com.elozino.splitify.server.feature.expense.expenseRoutes
import com.elozino.splitify.server.feature.group.GroupService
import com.elozino.splitify.server.feature.group.groupRoutes
import com.elozino.splitify.server.feature.notification.NotificationService
import com.elozino.splitify.server.feature.settlement.SettlementService
import com.elozino.splitify.server.feature.settlement.settlementRoutes
import com.elozino.splitify.server.feature.user.UserService
import com.elozino.splitify.server.feature.transfer.TransferService
import com.elozino.splitify.server.feature.transfer.activityRoutes
import com.elozino.splitify.server.feature.transfer.contactRoutes
import com.elozino.splitify.server.feature.transfer.notificationRoutes
import com.elozino.splitify.server.feature.transfer.transferRoutes
import com.elozino.splitify.server.feature.user.userRoutes
import com.elozino.splitify.server.feature.wallet.WalletService
import com.elozino.splitify.server.feature.wallet.walletRoutes
import com.elozino.splitify.shared.api.ApiRoutes
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
