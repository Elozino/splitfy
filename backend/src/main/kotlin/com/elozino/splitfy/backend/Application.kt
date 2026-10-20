package com.elozino.splitfy.backend

import com.elozino.splitfy.backend.di.serverModule
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
fun Application.module() {
    val config = AppConfig.from(this)
    log.info("Starting splitfy API in {} mode", config.environment)

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
    configureRouting(connection, config.features)
}
