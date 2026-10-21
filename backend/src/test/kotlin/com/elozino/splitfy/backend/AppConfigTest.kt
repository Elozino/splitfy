package com.elozino.splitfy.backend

import com.elozino.splitfy.backend.utils.AppConfig
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class AppConfigTest {

    private fun config(vararg overrides: Pair<String, String>) = MapApplicationConfig(
        "app.environment" to "PRODUCTION",
        "database.jdbcUrl" to "jdbc:postgresql://db:5432/splitfy",
        "database.username" to "splitfy",
        "database.password" to "a-real-production-password",
        "jwt.secret" to "a-production-secret-that-is-long-enough",
        "jwt.issuer" to "https://api.splitfy.app",
        "jwt.audience" to "splitfy-mobile",
        "security.totpEncryptionKey" to "a-production-totp-key-that-is-long-enough",
        *overrides,
    )

    @Test
    fun `production boots with real secrets`() {
        AppConfig.from(config())
    }

    @Test
    fun `production refuses the development database password`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AppConfig.from(config("database.password" to AppConfig.INSECURE_DEV_DATABASE_PASSWORD))
        }
        assertEquals("Refusing to start production with the development database password", error.message)
    }

    @Test
    fun `production refuses the development JWT secret`() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.from(config("jwt.secret" to AppConfig.INSECURE_DEV_SECRET))
        }
    }

    @Test
    fun `local development may use the default database password`() {
        AppConfig.from(
            config(
                "app.environment" to "LOCAL",
                "database.password" to AppConfig.INSECURE_DEV_DATABASE_PASSWORD,
            )
        )
    }

    @Test
    fun `production refuses to start without a TOTP encryption key`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AppConfig.from(config("security.totpEncryptionKey" to ""))
        }
        assertEquals("security.totpEncryptionKey (TOTP_ENCRYPTION_KEY) is required in production", error.message)
    }

    @Test
    fun `statement timeout defaults to thirty seconds`() {
        assertEquals(30.seconds, AppConfig.from(config()).database.statementTimeout)
    }
}
