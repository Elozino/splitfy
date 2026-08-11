package com.elozino.splitfy.backend.utils

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Typed configuration, read once at start-up.
 *
 * Every value comes from `application.yaml`, which itself only ever references
 * environment variables — no secret is ever committed to the repository, and a
 * missing one fails the boot loudly instead of silently defaulting.
 */
data class AppConfig(
    val environment: Environment,
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val security: SecurityConfig,
    val cors: CorsConfig,
) {
    enum class Environment {
        LOCAL, STAGING, PRODUCTION;

        val isProduction: Boolean get() = this == PRODUCTION
    }

    data class DatabaseConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String,
        val driver: String,
        val maxPoolSize: Int,
        val runMigrations: Boolean,
    )

    data class JwtConfig(
        val secret: String,
        val issuer: String,
        val audience: String,
        val realm: String,
        val accessTokenTtl: Duration,
        val refreshTokenTtl: Duration,
    )

    data class SecurityConfig(
        val bcryptCost: Int,
        val otpTtl: Duration,
    )

    data class CorsConfig(
        val allowedHosts: List<String>,
    )

    companion object {

        fun from(application: Application): AppConfig = from(application.environment.config)

        fun from(config: ApplicationConfig): AppConfig {
            val environment = Environment.valueOf(
                config.propertyOrNull("app.environment")?.getString()?.uppercase() ?: "LOCAL"
            )

            val jwtSecret = config.property("jwt.secret").getString()
            require(jwtSecret.length >= MIN_JWT_SECRET_LENGTH) {
                "jwt.secret must be at least $MIN_JWT_SECRET_LENGTH characters"
            }
            require(!(environment.isProduction && jwtSecret == INSECURE_DEV_SECRET)) {
                "Refusing to start production with the development JWT secret"
            }

            return AppConfig(
                environment = environment,
                database = DatabaseConfig(
                    jdbcUrl = config.property("database.jdbcUrl").getString(),
                    username = config.property("database.username").getString(),
                    password = config.property("database.password").getString(),
                    driver = config.propertyOrNull("database.driver")?.getString()
                        ?: "org.postgresql.Driver",
                    maxPoolSize = config.propertyOrNull("database.maxPoolSize")?.getString()?.toInt() ?: 10,
                    runMigrations = config.propertyOrNull("database.runMigrations")?.getString()
                        ?.toBooleanStrict() ?: true,
                ),
                jwt = JwtConfig(
                    secret = jwtSecret,
                    issuer = config.property("jwt.issuer").getString(),
                    audience = config.property("jwt.audience").getString(),
                    realm = config.propertyOrNull("jwt.realm")?.getString() ?: "splitfy",
                    accessTokenTtl = config.propertyOrNull("jwt.accessTokenMinutes")?.getString()
                        ?.toLong()?.minutes ?: 15.minutes,
                    refreshTokenTtl = config.propertyOrNull("jwt.refreshTokenDays")?.getString()
                        ?.toLong()?.days ?: 30.days,
                ),
                security = SecurityConfig(
                    bcryptCost = config.propertyOrNull("security.bcryptCost")?.getString()?.toInt() ?: 12,
                    otpTtl = config.propertyOrNull("security.otpMinutes")?.getString()
                        ?.toLong()?.minutes ?: 10.minutes,
                ),
                cors = CorsConfig(
                    allowedHosts = config.propertyOrNull("cors.allowedHosts")?.getList().orEmpty(),
                ),
            )
        }

        const val INSECURE_DEV_SECRET = "local-development-secret-do-not-use-in-production"
        private const val MIN_JWT_SECRET_LENGTH = 32
    }
}
