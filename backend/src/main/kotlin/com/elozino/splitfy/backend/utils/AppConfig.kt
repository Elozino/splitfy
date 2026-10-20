package com.elozino.splitfy.backend.utils

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    val features: FeaturesConfig = FeaturesConfig(),
    val email: EmailConfig = EmailConfig(),
    val oauth: OAuthConfig = OAuthConfig(),
    val links: LinksConfig = LinksConfig(),
    val push: PushConfig = PushConfig(),
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
        /** Upper bound on any single statement, so one bad query cannot hold a pooled connection. */
        val statementTimeout: Duration,
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

    data class EmailConfig(
        /** Resend API key. Blank means "log codes instead", which production refuses. */
        val resendApiKey: String = "",
        val from: String = "splitfy <no-reply@splitfy.app>",
    )

    data class OAuthConfig(
        /** Every Google OAuth client id whose ID tokens we accept (Android, iOS, web). Empty disables Google. */
        val googleClientIds: Set<String> = emptySet(),
        /** Apple audiences: the iOS bundle id, plus a Services ID if web sign-in is added. Empty disables Apple. */
        val appleClientIds: Set<String> = emptySet(),
    )

    data class LinksConfig(
        /** Where invite links point. That host must route the invite and .well-known paths to this API. */
        val publicBaseUrl: String = "https://splitfy.app",
        val androidPackage: String = "com.elozino.splitfy",
        /** SHA-256 fingerprints of the release signing certificate(s), as `AA:BB:...`. */
        val androidCertSha256: Set<String> = emptySet(),
        /** `TEAMID.bundle.id` entries allowed to open links on iOS. */
        val appleAppIds: Set<String> = emptySet(),
    )

    data class PushConfig(
        /** A Firebase service-account key, as JSON. Wins over [fcmServiceAccountPath]. */
        val fcmServiceAccountJson: String = "",
        /** Or the path of that key file. Both blank means push is logged, not sent. */
        val fcmServiceAccountPath: String = "",
        /** Off unless configured, so tests and one-off tools never start the scheduler. */
        val remindersEnabled: Boolean = false,
        /** A debt has to be this old before its first reminder. */
        val reminderAfterDays: Int = 3,
        /** And then at most one reminder per group this often. */
        val reminderEveryDays: Int = 7,
        /** When the daily run starts, in UTC. */
        val reminderHourUtc: Int = 9,
    ) {
        val isFcmConfigured: Boolean get() = fcmServiceAccountJson.isNotBlank() || fcmServiceAccountPath.isNotBlank()
    }

    data class FeaturesConfig(
        /**
         * The in-app wallet: balances, top-up, withdrawal, payment methods and
         * peer transfers. Off until a payment provider backs it — today a top-up
         * credits the balance without charging anyone, so it must not be reachable.
         */
        val wallet: Boolean = false,
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

            val databasePassword = config.property("database.password").getString()
            require(!(environment.isProduction && databasePassword == INSECURE_DEV_DATABASE_PASSWORD)) {
                "Refusing to start production with the development database password"
            }

            return AppConfig(
                environment = environment,
                database = DatabaseConfig(
                    jdbcUrl = config.property("database.jdbcUrl").getString(),
                    username = config.property("database.username").getString(),
                    password = databasePassword,
                    driver = config.propertyOrNull("database.driver")?.getString()
                        ?: "org.postgresql.Driver",
                    maxPoolSize = config.propertyOrNull("database.maxPoolSize")?.getString()?.toInt() ?: 10,
                    runMigrations = config.propertyOrNull("database.runMigrations")?.getString()
                        ?.toBooleanStrict() ?: true,
                    statementTimeout = config.propertyOrNull("database.statementTimeoutSeconds")?.getString()
                        ?.toLong()?.seconds ?: 30.seconds,
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
                email = EmailConfig(
                    resendApiKey = config.propertyOrNull("email.resendApiKey")?.getString().orEmpty(),
                    from = config.propertyOrNull("email.from")?.getString() ?: EmailConfig().from,
                ),
                oauth = OAuthConfig(
                    googleClientIds = config.propertyOrNull("oauth.googleClientIds")?.getString().csv(),
                    appleClientIds = config.propertyOrNull("oauth.appleClientIds")?.getString().csv(),
                ),
                links = LinksConfig(
                    publicBaseUrl = (config.propertyOrNull("links.publicBaseUrl")?.getString() ?: LinksConfig().publicBaseUrl)
                        .trimEnd('/'),
                    androidPackage = config.propertyOrNull("links.androidPackage")?.getString() ?: LinksConfig().androidPackage,
                    androidCertSha256 = config.propertyOrNull("links.androidCertSha256")?.getString().csv(),
                    appleAppIds = config.propertyOrNull("links.appleAppIds")?.getString().csv(),
                ),
                push = PushConfig(
                    fcmServiceAccountJson = config.propertyOrNull("push.fcmServiceAccountJson")?.getString().orEmpty(),
                    fcmServiceAccountPath = config.propertyOrNull("push.fcmServiceAccountPath")?.getString().orEmpty(),
                    remindersEnabled = config.propertyOrNull("push.remindersEnabled")?.getString()
                        ?.toBooleanStrict() ?: PushConfig().remindersEnabled,
                    reminderAfterDays = config.propertyOrNull("push.reminderAfterDays")?.getString()?.toInt()
                        ?: PushConfig().reminderAfterDays,
                    reminderEveryDays = config.propertyOrNull("push.reminderEveryDays")?.getString()?.toInt()
                        ?: PushConfig().reminderEveryDays,
                    reminderHourUtc = (config.propertyOrNull("push.reminderHourUtc")?.getString()?.toInt()
                        ?: PushConfig().reminderHourUtc).also { require(it in 0..23) { "push.reminderHourUtc must be 0-23" } },
                ),
                features = FeaturesConfig(
                    wallet = config.propertyOrNull("features.wallet")?.getString()?.toBooleanStrict() ?: false,
                ),
            )
        }

        private fun String?.csv(): Set<String> =
            orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        const val INSECURE_DEV_SECRET = "local-development-secret-do-not-use-in-production"
        const val INSECURE_DEV_DATABASE_PASSWORD = "splitfy"
        private const val MIN_JWT_SECRET_LENGTH = 32
    }
}
