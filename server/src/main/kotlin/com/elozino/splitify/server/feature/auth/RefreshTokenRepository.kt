package com.elozino.splitify.server.feature.auth

import com.elozino.splitify.server.db.PasswordResetsTable
import com.elozino.splitify.server.db.RefreshTokensTable
import com.elozino.splitify.server.db.dbQuery
import com.elozino.splitify.server.db.toJava
import com.elozino.splitify.server.db.toKotlin
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

data class StoredRefreshToken(
    val id: UUID,
    val userId: UUID,
    val expiresAt: Instant,
    val revokedAt: Instant?,
) {
    fun isUsable(now: Instant): Boolean = revokedAt == null && expiresAt > now
}

/**
 * Refresh-token storage with rotation.
 *
 * Each refresh consumes the presented token and issues a new one. Presenting an
 * already-consumed token is a strong signal of theft, so the whole device family is
 * revoked rather than just the replayed token.
 */
class RefreshTokenRepository {

    suspend fun store(
        userId: UUID,
        tokenHash: String,
        deviceId: String?,
        expiresAt: Instant,
    ): UUID = dbQuery {
        val id = UUID.randomUUID()
        RefreshTokensTable.insert {
            it[RefreshTokensTable.id] = id
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.tokenHash] = tokenHash
            it[RefreshTokensTable.deviceId] = deviceId
            it[RefreshTokensTable.expiresAt] = expiresAt.toJava()
            it[createdAt] = Clock.System.now().toJava()
        }
        id
    }

    suspend fun findByHash(tokenHash: String): StoredRefreshToken? = dbQuery {
        RefreshTokensTable.selectAll()
            .where { RefreshTokensTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?.let {
                StoredRefreshToken(
                    id = it[RefreshTokensTable.id],
                    userId = it[RefreshTokensTable.userId],
                    expiresAt = it[RefreshTokensTable.expiresAt].toKotlin(),
                    revokedAt = it[RefreshTokensTable.revokedAt]?.toKotlin(),
                )
            }
    }

    suspend fun revoke(id: UUID): Boolean = dbQuery {
        RefreshTokensTable.update({
            (RefreshTokensTable.id eq id) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[revokedAt] = Clock.System.now().toJava()
        } > 0
    }

    suspend fun revokeByHash(tokenHash: String): Boolean = dbQuery {
        RefreshTokensTable.update({
            (RefreshTokensTable.tokenHash eq tokenHash) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[revokedAt] = Clock.System.now().toJava()
        } > 0
    }

    /** Used on replay detection and on "sign out of all devices". */
    suspend fun revokeAllForUser(userId: UUID): Int = dbQuery {
        RefreshTokensTable.update({
            (RefreshTokensTable.userId eq userId) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[revokedAt] = Clock.System.now().toJava()
        }
    }
}

/** One-time codes for the forgot-password flow. Codes are stored hashed. */
class PasswordResetRepository {

    suspend fun create(userId: UUID, codeHash: String, expiresAt: Instant): UUID = dbQuery {
        val id = UUID.randomUUID()
        PasswordResetsTable.insert {
            it[PasswordResetsTable.id] = id
            it[PasswordResetsTable.userId] = userId
            it[PasswordResetsTable.codeHash] = codeHash
            it[PasswordResetsTable.expiresAt] = expiresAt.toJava()
            it[createdAt] = Clock.System.now().toJava()
        }
        id
    }

    suspend fun findActive(userId: UUID, codeHash: String, now: Instant): UUID? = dbQuery {
        PasswordResetsTable.selectAll()
            .where {
                (PasswordResetsTable.userId eq userId) and
                    (PasswordResetsTable.codeHash eq codeHash) and
                    PasswordResetsTable.consumedAt.isNull() and
                    (PasswordResetsTable.expiresAt greater now.toJava())
            }
            .singleOrNull()
            ?.get(PasswordResetsTable.id)
    }

    suspend fun consume(id: UUID): Boolean = dbQuery {
        PasswordResetsTable.update({
            (PasswordResetsTable.id eq id) and PasswordResetsTable.consumedAt.isNull()
        }) {
            it[consumedAt] = Clock.System.now().toJava()
        } > 0
    }
}
