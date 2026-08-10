package com.elozino.splitify.server

import com.elozino.splitify.server.db.ActivityLogTable
import com.elozino.splitify.server.db.ExpenseSharesTable
import com.elozino.splitify.server.db.ExpensesTable
import com.elozino.splitify.server.db.GroupMembersTable
import com.elozino.splitify.server.db.GroupsTable
import com.elozino.splitify.server.db.PasswordResetsTable
import com.elozino.splitify.server.db.RefreshTokensTable
import com.elozino.splitify.server.db.ContactFavoritesTable
import com.elozino.splitify.server.db.LedgerEntriesTable
import com.elozino.splitify.server.db.NotificationsTable
import com.elozino.splitify.server.db.PaymentMethodsTable
import com.elozino.splitify.server.db.SettlementsTable
import com.elozino.splitify.server.db.TransfersTable
import com.elozino.splitify.server.db.WalletsTable
import com.elozino.splitify.server.db.UsersTable
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Boots the real application against an in-memory H2 database.
 *
 * The schema is created from the Exposed table definitions rather than by running
 * Flyway, because the migrations are written for PostgreSQL. That means these tests
 * verify behaviour, not the migration scripts — those are exercised by running the
 * stack against the Postgres container in `docker-compose.yml`.
 */
class TestDatabase(name: String) {

    /**
     * One in-memory database per test class.
     *
     * Sharing a single H2 instance let one class drop tables while another class's
     * connection pool was still closing, which showed up as a rare, confusing
     * failure in an unrelated test.
     */
    val jdbcUrl = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"

    private val tables = arrayOf(
        UsersTable,
        RefreshTokensTable,
        PasswordResetsTable,
        GroupsTable,
        GroupMembersTable,
        ExpensesTable,
        ExpenseSharesTable,
        SettlementsTable,
        ActivityLogTable,
        WalletsTable,
        PaymentMethodsTable,
        TransfersTable,
        LedgerEntriesTable,
        NotificationsTable,
        ContactFavoritesTable,
    )

    /** Resets to an empty schema so each test starts from a known state. */
    fun reset() {
        Database.connect(jdbcUrl, driver = "org.h2.Driver", user = "sa", password = "")
        transaction {
            SchemaUtils.drop(*tables.reversedArray())
            SchemaUtils.create(*tables)
        }
    }
}

/**
 * Test configuration: H2, migrations off, and a throwaway signing secret.
 *
 * The module is installed explicitly because replacing the config also drops the
 * `ktor.application.modules` entry that `application.yaml` normally provides.
 */
fun ApplicationTestBuilder.useTestConfig(database: TestDatabase) {
    application { module() }
    environment {
        config = MapApplicationConfig(
            "app.environment" to "LOCAL",
            "database.jdbcUrl" to database.jdbcUrl,
            "database.username" to "sa",
            "database.password" to "",
            "database.driver" to "org.h2.Driver",
            "database.maxPoolSize" to "2",
            "database.runMigrations" to "false",
            "jwt.secret" to "test-secret-that-is-long-enough-to-pass",
            "jwt.issuer" to "https://test.splitfy.app",
            "jwt.audience" to "splitfy-test",
            "jwt.realm" to "splitfy",
            "jwt.accessTokenMinutes" to "15",
            "jwt.refreshTokenDays" to "30",
            "security.bcryptCost" to "4",
            "security.otpMinutes" to "10",
        )
    }
}

/** A JSON-aware client; the default test client has no content negotiation. */
fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
}
