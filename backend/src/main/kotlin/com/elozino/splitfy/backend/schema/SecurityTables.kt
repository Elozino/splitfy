package com.elozino.splitfy.backend.schema

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** One signed-in device: the chain of refresh tokens that rotate into one another. */
object AuthSessionsTable : Table("auth_sessions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceName = varchar("device_name", 100).nullable()
    val platform = varchar("platform", 20).nullable()
    val createdAt = timestamp("created_at")
    val lastUsedAt = timestamp("last_used_at")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_auth_sessions_user", false, userId)
    }
}

object UserTotpTable : Table("user_totp") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val secretEncrypted = text("secret_encrypted")
    val enabledAt = timestamp("enabled_at").nullable()
    val lastUsedStep = long("last_used_step").default(0)
    val failedAttempts = integer("failed_attempts").default(0)
    val lastFailedAt = timestamp("last_failed_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId)
}

object TotpRecoveryCodesTable : Table("totp_recovery_codes") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val codeHash = varchar("code_hash", 64)
    val usedAt = timestamp("used_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_totp_recovery_codes_user", false, userId)
    }
}

object TwoFactorChallengesTable : Table("two_factor_challenges") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 64)
    val deviceId = varchar("device_id", 128).nullable()
    val expiresAt = timestamp("expires_at")
    val attempts = integer("attempts").default(0)
    val consumedAt = timestamp("consumed_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_two_factor_challenges_token", tokenHash)
        index("ix_two_factor_challenges_user", false, userId)
    }
}
