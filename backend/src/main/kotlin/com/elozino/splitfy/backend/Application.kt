package com.elozino.splitfy.backend

import org.koin.core.module.Module
import com.elozino.splitfy.backend.di.serverModule
import com.elozino.splitfy.backend.plugins.configureErrorReporting
import com.elozino.splitfy.backend.plugins.configureHttp
import com.elozino.splitfy.backend.plugins.configureObservability
import com.elozino.splitfy.backend.plugins.configureRateLimiting
import com.elozino.splitfy.backend.plugins.configureRouting
import com.elozino.splitfy.backend.plugins.configureSecurity
import com.elozino.splitfy.backend.plugins.configureStatusPages
import com.elozino.splitfy.backend.schema.DatabaseFactory
import com.elozino.splitfy.backend.utils.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

/**
 * Referenced from `application.yaml` (`ktor.application.modules`).
 *
 * Order matters: configuration and DI first, then transport, then security, then
 * routes — a route registered before its authentication provider exists would fail
 * at start-up rather than at request time.
 */
fun Application.module() = configureApp(overrides = emptyList())

/**
 * The real start-up sequence. [overrides] are Koin modules loaded after the server's
 * own, so a test can replace one binding (an email sender, say) and keep the rest.
 */
fun Application.configureApp(overrides: List<Module>) {
    val config = AppConfig.from(this)
    log.info("Starting splitfy API in {} mode", config.environment)

    install(Koin) {
        slf4jLogger()
        modules(listOf(serverModule(config)) + overrides)
    }

    val database = DatabaseFactory(config.database)
    val connection = database.connect()
    monitor.subscribe(ApplicationStopped) {
        log.info("Closing database connection pool")
        database.close()
    }

    configureErrorReporting(config)
    configureHttp(config)
    val metrics = configureObservability()
    configureSecurity()
    configureRateLimiting()
    configureStatusPages()
    configureRouting(connection, config, metrics)
}
