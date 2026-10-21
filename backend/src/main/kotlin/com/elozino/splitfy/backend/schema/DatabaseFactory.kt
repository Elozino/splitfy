package com.elozino.splitfy.backend.schema

import com.elozino.splitfy.backend.utils.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

/**
 * Owns the connection pool and the schema lifecycle.
 *
 * The pool is created once at boot and closed on shutdown; migrations run before the
 * first query so a rolling deploy can never serve traffic against an old schema.
 */
class DatabaseFactory(private val config: AppConfig.DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private lateinit var dataSource: HikariDataSource

    fun connect(): Database {
        dataSource = HikariDataSource(hikariConfig())
        if (config.runMigrations) migrate(dataSource)
        logger.info("Connected to {}", config.jdbcUrl.substringBefore("?"))
        return Database.connect(dataSource).also(ActiveDatabase::install)
    }

    /**
     * Applies pending migrations and returns how many ran, without starting a pool
     * for the application. Backs the standalone `Migrate` entry point.
     */
    // Two connections: Flyway holds one for its lock while it migrates on another.
    fun migrateOnly(): Int = HikariDataSource(hikariConfig().apply { maximumPoolSize = 2 }).use { source ->
        migrate(source)
    }

    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) dataSource.close()
        ActiveDatabase.clear()
    }

    private fun hikariConfig() = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.username
        password = config.password
        driverClassName = config.driver
        maximumPoolSize = config.maxPoolSize
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        // Fail fast rather than queueing requests behind an exhausted pool.
        connectionTimeout = CONNECTION_TIMEOUT_MS
        // Applied per session by the server, so it also bounds statements Exposed issues.
        addDataSourceProperty("options", "-c statement_timeout=${config.statementTimeout.inWholeMilliseconds}")
        validate()
    }

    private fun migrate(source: DataSource): Int =
        Flyway.configure()
            .dataSource(source)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()
            .migrationsExecuted

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 10_000L
    }
}

/**
 * The database this process is currently serving from.
 *
 * Exposed will otherwise fall back to whichever `Database` was registered last
 * globally, which is not the same thing: during a restart — or between two
 * applications in one test JVM — that can still be an instance whose connection
 * pool has already been closed. Naming the database explicitly makes every query
 * unambiguous.
 */
object ActiveDatabase {

    @Volatile
    private var current: Database? = null

    fun install(database: Database) {
        current = database
    }

    fun clear() {
        current = null
    }

    fun require(): Database = current
        ?: error("No database is connected - Application.module() has not run yet")
}

/**
 * Runs [block] in a suspended transaction on the IO dispatcher, against the
 * currently connected database.
 *
 * Every repository call goes through this instead of Exposed's blocking
 * `transaction {}`, which would otherwise park a Netty event-loop thread.
 */
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO, db = ActiveDatabase.require()) { block() }
