package com.elozino.splitfy.backend

import org.koin.core.module.Module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.DriverManager
import kotlinx.serialization.json.Json

/**
 * One PostgreSQL server for the whole test JVM, started on first use.
 *
 * These are real Postgres binaries, not an emulation, so the Flyway migrations run
 * exactly as they do in production — a broken migration fails the build here rather
 * than at deploy time.
 */
private object TestPostgres {
    val server: EmbeddedPostgres by lazy {
        EmbeddedPostgres.start().also { pg ->
            Runtime.getRuntime().addShutdownHook(Thread { pg.close() })
        }
    }

    const val USER = "postgres"
    const val PASSWORD = "postgres"

    fun jdbcUrl(database: String) = server.getJdbcUrl(USER, database)
}

/**
 * One database per test class on the shared server.
 *
 * Separate databases keep classes from interfering with one another; [reset] drops
 * the schema so the application's own Flyway run rebuilds it before every test.
 */
class TestDatabase(private val name: String) {

    val jdbcUrl: String get() = TestPostgres.jdbcUrl(name)

    /** Recreates an empty database so each test starts from a known state. */
    fun reset() {
        DriverManager.getConnection(TestPostgres.jdbcUrl("postgres"), TestPostgres.USER, TestPostgres.PASSWORD)
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DROP DATABASE IF EXISTS $name WITH (FORCE)")
                    statement.execute("CREATE DATABASE $name")
                }
            }
    }

    /** Marks an account's email as verified without going through the code flow. */
    fun markVerified(email: String) {
        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.prepareStatement("UPDATE users SET email_verified_at = now() WHERE email = ?").use {
                it.setString(1, email)
                it.executeUpdate()
            }
        }
    }

    val username: String get() = TestPostgres.USER
    val password: String get() = TestPostgres.PASSWORD
}

/**
 * Test configuration: embedded Postgres, migrations on, and a throwaway signing secret.
 *
 * The module is installed explicitly because replacing the config also drops the
 * `ktor.application.modules` entry that `application.yaml` normally provides.
 */
fun ApplicationTestBuilder.useTestConfig(
    database: TestDatabase,
    walletEnabled: Boolean = true,
    overrides: List<Module> = emptyList(),
    environment: String = "LOCAL",
    extraConfig: Map<String, String> = emptyMap(),
) {
    application { configureApp(overrides) }
    environment {
        config = MapApplicationConfig(
            "app.environment" to environment,
            "database.jdbcUrl" to database.jdbcUrl,
            "database.username" to database.username,
            "database.password" to database.password,
            "database.driver" to "org.postgresql.Driver",
            "database.maxPoolSize" to "2",
            "database.runMigrations" to "true",
            "jwt.secret" to "test-secret-that-is-long-enough-to-pass",
            "jwt.issuer" to "https://test.splitfy.app",
            "jwt.audience" to "splitfy-test",
            "jwt.realm" to "splitfy",
            "jwt.accessTokenMinutes" to "15",
            "jwt.refreshTokenDays" to "30",
            "security.bcryptCost" to "4",
            "security.otpMinutes" to "10",
            "features.wallet" to walletEnabled.toString(),
            *extraConfig.toList().toTypedArray(),
        )
    }
}

/** A JSON-aware client; the default test client has no content negotiation. */
fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
}
