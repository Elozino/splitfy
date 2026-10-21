package com.elozino.splitfy.backend

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrateTest {

    private val database = TestDatabase("splitfy_migrate")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `the standalone migrator applies every migration once`() {
        val config = MapApplicationConfig(
            "database.jdbcUrl" to database.jdbcUrl,
            "database.username" to database.username,
            "database.password" to database.password,
            "jwt.secret" to "test-secret-that-is-long-enough-to-pass",
            "jwt.issuer" to "https://test.splitfy.app",
            "jwt.audience" to "splitfy-test",
        )

        val first = runMigrations(config)
        assertTrue(first > 0, "applied $first")
        assertEquals(0, runMigrations(config))
    }
}
