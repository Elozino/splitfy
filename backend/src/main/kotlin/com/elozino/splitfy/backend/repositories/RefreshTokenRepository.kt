package com.elozino.splitfy.backend.repositories

import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SortOrder
import com.elozino.splitfy.backend.schema.*
import org.jetbrains.exposed.sql.and
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

/** A pending change of address, as stored in `email_changes`. */
data class PendingEmailChange(val id: UUID, val newEmail: String, val expiresAt: Instant)

/** Codes that move an account to a new address; the code is sent to that address. */
class EmailChangeRepository : OneTimeCodeRepository(EmailChangesTable) {

    /** Replaces any change still pending, so only the newest code works. */
    suspend fun createChange(userId: UUID, newEmail: String, codeHash: String, expiresAt: Instant): UUID {
        cancelPending(userId)
        return dbQuery {
            val id = UUID.randomUUID()
            EmailChangesTable.insert {
                it[EmailChangesTable.id] = id
                it[EmailChangesTable.userId] = userId
                it[EmailChangesTable.newEmail] = newEmail
                it[EmailChangesTable.codeHash] = codeHash
                it[EmailChangesTable.expiresAt] = expiresAt.toJava()
                it[EmailChangesTable.createdAt] = Clock.System.now().toJava()
            }
            id
        }
    }

    suspend fun findActiveChange(userId: UUID, codeHash: String, now: Instant): PendingEmailChange? {
        val id = findActive(userId, codeHash, now) ?: return null
        return dbQuery {
            EmailChangesTable.selectAll().where { EmailChangesTable.id eq id }.singleOrNull()?.let {
                PendingEmailChange(id, it[EmailChangesTable.newEmail], it[EmailChangesTable.expiresAt].toKotlin())
            }
        }
    }

    /** How many changes [userId] has started since [since], for the daily cap. */
    suspend fun countIssuedSince(userId: UUID, since: Instant): Long = dbQuery {
        EmailChangesTable.selectAll()
            .where { (EmailChangesTable.userId eq userId) and (EmailChangesTable.createdAt greaterEq since.toJava()) }
            .count()
    }

    suspend fun cancelPending(userId: UUID): Unit = dbQuery {
        EmailChangesTable.update({ (EmailChangesTable.userId eq userId) and EmailChangesTable.consumedAt.isNull() }) {
            it[consumedAt] = Clock.System.now().toJava()
        }
    }
}
