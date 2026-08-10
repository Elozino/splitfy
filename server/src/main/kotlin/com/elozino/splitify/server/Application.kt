package com.elozino.splitify.server

import com.elozino.splitify.server.config.AppConfig
import com.elozino.splitify.server.db.DatabaseFactory
import com.elozino.splitify.server.di.serverModule
import com.elozino.splitify.server.plugins.configureHttp
import com.elozino.splitify.server.plugins.configureObservability
import com.elozino.splitify.server.plugins.configureRateLimiting
import com.elozino.splitify.server.plugins.configureRouting
import com.elozino.splitify.server.plugins.configureSecurity
import com.elozino.splitify.server.plugins.configureStatusPages
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
fun Application.module() {
    val config = AppConfig.from(this)
    log.info("Starting Splitfy API in {} mode", config.environment)

    install(Koin) {
        slf4jLogger()
        modules(serverModule(config))
    }

    val database = DatabaseFactory(config.database)
    val connection = database.connect()
    monitor.subscribe(ApplicationStopped) {
        log.info("Closing database connection pool")
        database.close()
    }

    configureHttp(config)
    configureObservability()
    configureSecurity()
    configureRateLimiting()
    configureStatusPages()
    configureRouting(connection)
}
