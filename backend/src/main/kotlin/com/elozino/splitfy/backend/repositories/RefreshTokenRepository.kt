package com.elozino.splitfy.backend.repositories

import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SortOrder
import com.elozino.splitfy.backend.schema.*
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.*
import kotlin.time.Clock
import kotlin.time.Instant

data class StoredRefreshToken(
    val id: UUID,
    val userId: UUID,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    /** Null only for tokens issued before sessions existed and not yet rotated. */
    val sessionId: UUID? = null,
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
        sessionId: UUID? = null,
    ): UUID = dbQuery {
        val id = UUID.randomUUID()
        RefreshTokensTable.insert {
            it[RefreshTokensTable.id] = id
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.tokenHash] = tokenHash
            it[RefreshTokensTable.deviceId] = deviceId
            it[RefreshTokensTable.sessionId] = sessionId
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
                    sessionId = it[RefreshTokensTable.sessionId],
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

    /** Signing out: the token stops working and its device leaves the session list. */
    suspend fun revokeByHash(tokenHash: String): Boolean = dbQuery {
        val now = Clock.System.now().toJava()
        val sessionId = RefreshTokensTable.selectAll()
            .where { RefreshTokensTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?.get(RefreshTokensTable.sessionId)
        sessionId?.let { id ->
            AuthSessionsTable.update({ (AuthSessionsTable.id eq id) and AuthSessionsTable.revokedAt.isNull() }) {
                it[revokedAt] = now
            }
        }
        RefreshTokensTable.update({
            (RefreshTokensTable.tokenHash eq tokenHash) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[revokedAt] = now
        } > 0
    }

    /** Used on replay detection and on "sign out of all devices". */
    suspend fun revokeAllForUser(userId: UUID): Int = dbQuery {
        val now = Clock.System.now().toJava()
        AuthSessionsTable.update({ (AuthSessionsTable.userId eq userId) and AuthSessionsTable.revokedAt.isNull() }) {
            it[revokedAt] = now
        }
        RefreshTokensTable.update({
            (RefreshTokensTable.userId eq userId) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[revokedAt] = now
        }
    }

    // --- Sessions -------------------------------------------------------------

    suspend fun createSession(userId: UUID, device: DeviceLabel, expiresAt: Instant): UUID = dbQuery {
        val id = UUID.randomUUID()
        val now = dbNow().toJava()
        AuthSessionsTable.insert {
            it[AuthSessionsTable.id] = id
            it[AuthSessionsTable.userId] = userId
            it[deviceName] = device.name
            it[platform] = device.platform
            it[createdAt] = now
            it[lastUsedAt] = now
            it[AuthSessionsTable.expiresAt] = expiresAt.toJava()
        }
        id
    }

    /** A refresh: the session lives on with a new expiry, and picks up a renamed device. */
    suspend fun touchSession(sessionId: UUID, device: DeviceLabel, expiresAt: Instant): Unit = dbQuery {
        AuthSessionsTable.update({ AuthSessionsTable.id eq sessionId }) {
            it[lastUsedAt] = dbNow().toJava()
            it[AuthSessionsTable.expiresAt] = expiresAt.toJava()
            device.name?.let { name -> it[deviceName] = name }
            device.platform?.let { value -> it[platform] = value }
        }
    }

    suspend fun isSessionRevoked(sessionId: UUID): Boolean = dbQuery {
        AuthSessionsTable.selectAll()
            .where { AuthSessionsTable.id eq sessionId }
            .singleOrNull()
            ?.let { it[AuthSessionsTable.revokedAt] != null }
            ?: true
    }

    /** Signed-in devices, most recently used first. */
    suspend fun activeSessions(userId: UUID, now: Instant): List<StoredSession> = dbQuery {
        AuthSessionsTable.selectAll()
            .where {
                (AuthSessionsTable.userId eq userId) and
                    AuthSessionsTable.revokedAt.isNull() and
                    (AuthSessionsTable.expiresAt greater now.toJava())
            }
            .orderBy(AuthSessionsTable.lastUsedAt, SortOrder.DESC)
            .map {
                StoredSession(
                    id = it[AuthSessionsTable.id],
                    deviceName = it[AuthSessionsTable.deviceName],
                    platform = it[AuthSessionsTable.platform],
                    createdAt = it[AuthSessionsTable.createdAt].toKotlin(),
                    lastUsedAt = it[AuthSessionsTable.lastUsedAt].toKotlin(),
                )
            }
    }

    /**
     * Ends one of [userId]'s sessions. Its refresh tokens are deleted rather than
     * marked revoked: a revoked token presented again reads as theft and signs the
     * user out everywhere, which is not what removing one device should do.
     */
    suspend fun revokeSession(userId: UUID, sessionId: UUID): Boolean = dbQuery {
        val revoked = AuthSessionsTable.update({
            (AuthSessionsTable.id eq sessionId) and (AuthSessionsTable.userId eq userId) and
                AuthSessionsTable.revokedAt.isNull()
        }) {
            it[revokedAt] = dbNow().toJava()
        } > 0
        if (revoked) RefreshTokensTable.deleteWhere { RefreshTokensTable.sessionId eq sessionId }
        revoked
    }

    /** "Sign out of all other devices", and what a password change does. [keep] null ends them all. */
    suspend fun revokeOtherSessions(userId: UUID, keep: UUID?): Int = dbQuery {
        val others = AuthSessionsTable.selectAll()
            .where {
                (AuthSessionsTable.userId eq userId) and AuthSessionsTable.revokedAt.isNull() and
                    (if (keep != null) AuthSessionsTable.id neq keep else Op.TRUE)
            }
            .map { it[AuthSessionsTable.id] }
        if (others.isNotEmpty()) {
            AuthSessionsTable.update({ AuthSessionsTable.id inList others }) { it[revokedAt] = dbNow().toJava() }
            RefreshTokensTable.deleteWhere { RefreshTokensTable.sessionId inList others }
        }
        // Tokens from before sessions existed belong to no session, so are never "this device".
        RefreshTokensTable.deleteWhere {
            (RefreshTokensTable.userId eq userId) and RefreshTokensTable.sessionId.isNull()
        }
        others.size
    }
}

/** What a client says about itself, cleaned up for storage. */
data class DeviceLabel(val name: String? = null, val platform: String? = null) {
    companion object {
        val UNKNOWN = DeviceLabel()
    }
}

data class StoredSession(
    val id: UUID,
    val deviceName: String?,
    val platform: String?,
    val createdAt: Instant,
    val lastUsedAt: Instant,
)

/** One-time codes for the forgot-password flow. Codes are stored hashed. */
/**
 * One-time codes for a purpose (password reset, email verification), each in its
 * own table so a code issued for one can never be redeemed for the other.
 */
open class OneTimeCodeRepository(private val table: OneTimeCodesTable) {

    suspend fun create(userId: UUID, codeHash: String, expiresAt: Instant): UUID = dbQuery {
        val id = UUID.randomUUID()
        table.insert {
            it[table.id] = id
            it[table.userId] = userId
            it[table.codeHash] = codeHash
            it[table.expiresAt] = expiresAt.toJava()
            it[table.createdAt] = Clock.System.now().toJava()
        }
        id
    }

    /** An unused, unexpired code that has not been guessed at too often. */
    suspend fun findActive(userId: UUID, codeHash: String, now: Instant): UUID? = dbQuery {
        table.selectAll()
            .where {
                (table.userId eq userId) and
                        (table.codeHash eq codeHash) and
                        table.consumedAt.isNull() and
                        (table.expiresAt greater now.toJava()) and
                        (table.attempts less MAX_ATTEMPTS)
            }
            .singleOrNull()
            ?.get(table.id)
    }

    /**
     * Counts a wrong guess against every live code for [userId]. Six digits is only a
     * million combinations; without this the per-IP rate limit was the only brake.
     */
    suspend fun recordFailedAttempt(userId: UUID, now: Instant): Unit = dbQuery {
        table.update({
            (table.userId eq userId) and
                table.consumedAt.isNull() and
                (table.expiresAt greater now.toJava())
        }) {
            it[table.attempts] = table.attempts + 1
        }
    }

    /** When the most recent code for [userId] was issued, for the resend cooldown. */
    suspend fun latestIssuedAt(userId: UUID): Instant? = dbQuery {
        table.selectAll()
            .where { table.userId eq userId }
            .orderBy(table.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(table.createdAt)
            ?.toKotlin()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }

    suspend fun consume(id: UUID): Boolean = dbQuery {
        table.update({
            (table.id eq id) and table.consumedAt.isNull()
        }) {
            it[table.consumedAt] = Clock.System.now().toJava()
        } > 0
    }
}

class PasswordResetRepository : OneTimeCodeRepository(PasswordResetsTable)

class EmailVerificationRepository : OneTimeCodeRepository(EmailVerificationsTable)
