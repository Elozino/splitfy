package com.elozino.splitfy.backend

import com.elozino.splitfy.backend.schema.ActivityLogTable
import com.elozino.splitfy.backend.schema.ContactFavoritesTable
import com.elozino.splitfy.backend.schema.EmailVerificationsTable
import com.elozino.splitfy.backend.schema.ExpenseSharesTable
import com.elozino.splitfy.backend.schema.ExpensesTable
import com.elozino.splitfy.backend.schema.GroupMembersTable
import com.elozino.splitfy.backend.schema.GroupsTable
import com.elozino.splitfy.backend.schema.LedgerEntriesTable
import com.elozino.splitfy.backend.schema.NotificationsTable
import com.elozino.splitfy.backend.schema.PasswordResetsTable
import com.elozino.splitfy.backend.schema.PaymentMethodsTable
import com.elozino.splitfy.backend.schema.RefreshTokensTable
import com.elozino.splitfy.backend.schema.SettlementsTable
import com.elozino.splitfy.backend.schema.TransfersTable
import com.elozino.splitfy.backend.schema.UserIdentitiesTable
import com.elozino.splitfy.backend.schema.UsersTable
import com.elozino.splitfy.backend.schema.WalletsTable
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks that the Flyway migrations and the Exposed table definitions describe the
 * same schema.
 *
 * The two are maintained by hand, side by side. A column added to a table object but
 * not to a migration compiles and passes review, then fails on the first query in
 * production; this turns that into a test failure naming the column.
 */
class DatabaseSchemaTest {

    private val database = TestDatabase("splitfy_schema")

    private val tables = listOf(
        com.elozino.splitfy.backend.payments.PaymentIntents,
        com.elozino.splitfy.backend.payments.PaymentEffects,
        UsersTable,
        RefreshTokensTable,
        PasswordResetsTable,
        EmailVerificationsTable,
        UserIdentitiesTable,
        GroupsTable,
        com.elozino.splitfy.backend.schema.GroupInvitesTable,
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

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `migrations create every column the table definitions use`() = testApplication {
        useTestConfig(database)
        startApplication()

        val migrated = migratedColumns()
        val problems = buildList {
            for (table in tables) {
                for (column in table.columns) {
                    val nullable = migrated[table.tableName to column.name]
                    when {
                        nullable == null ->
                            add("${table.tableName}.${column.name} is missing from the migrations")
                        nullable != column.columnType.nullable ->
                            add("${table.tableName}.${column.name} nullability differs (migration nullable=$nullable)")
                    }
                }
            }
        }

        assertEquals(emptyList(), problems)
    }

    /** `(table, column) -> is_nullable` for everything in the public schema. */
    private fun migratedColumns(): Map<Pair<String, String>, Boolean> =
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT table_name, column_name, is_nullable FROM information_schema.columns " +
                        "WHERE table_schema = 'public'"
                ).use { rows ->
                    buildMap {
                        while (rows.next()) {
                            put(rows.getString(1) to rows.getString(2), rows.getString(3) == "YES")
                        }
                    }
                }
            }
        }
}
