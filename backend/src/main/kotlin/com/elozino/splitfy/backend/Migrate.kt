package com.elozino.splitfy.backend

import com.elozino.splitfy.backend.schema.DatabaseFactory
import com.elozino.splitfy.backend.utils.AppConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import kotlin.system.exitProcess
import org.slf4j.LoggerFactory

/**
 * Applies database migrations and exits, for platforms that run a release or
 * pre-deploy step before rolling out new instances:
 *
 *     java -cp /app/app.jar com.elozino.splitfy.backend.MigrateKt
 *
 * Reads the same environment variables as the server. Pair it with
 * `DATABASE_RUN_MIGRATIONS=false` on the server so instances never race to migrate
 * (Flyway's lock makes that safe, but a failed migration then fails the deploy
 * instead of every instance's boot).
 */
fun main() {
    val logger = LoggerFactory.getLogger("Migrate")
    val config = checkNotNull(YamlConfig("application.yaml")) { "application.yaml is missing from the classpath" }
    val applied = runCatching { runMigrations(config) }.getOrElse {
        logger.error("Migration failed", it)
        exitProcess(1)
    }
    logger.info("Applied {} migration(s)", applied)
}

internal fun runMigrations(config: ApplicationConfig): Int =
    DatabaseFactory(AppConfig.from(config).database).migrateOnly()
