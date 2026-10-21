package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.backend.schema.TotpRecoveryCodesTable
import com.elozino.splitfy.backend.schema.TwoFactorChallengesTable
import com.elozino.splitfy.backend.schema.UserTotpTable
import com.elozino.splitfy.backend.schema.dbNow
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.schema.toKotlin
import java.util.UUID
import kotlin.time.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

data class TotpRecord(
    val secretEncrypted: String,
    val enabledAt: Instant?,
    val lastUsedStep: Long,
    val failedAttempts: Int,
    val lastFailedAt: Instant?,
) {
    val isEnabled: Boolean get() = enabledAt != null
}

data class StoredChallenge(
    val id: UUID,
    val userId: UUID,
    val deviceId: String?,
    val expiresAt: Instant,
    val attempts: Int,
    val consumedAt: Instant?,
)

/** Authenticator secrets, recovery codes and pending sign-in challenges. */
class TwoFactorRepository {

    suspend fun find(userId: UUID): TotpRecord? = dbQuery {
        UserTotpTable.selectAll()
            .where { UserTotpTable.userId eq userId }
            .singleOrNull()
            ?.let {
                TotpRecord(
                    secretEncrypted = it[UserTotpTable.secretEncrypted],
                    enabledAt = it[UserTotpTable.enabledAt]?.toKotlin(),
                    lastUsedStep = it[UserTotpTable.lastUsedStep],
                    failedAttempts = it[UserTotpTable.failedAttempts],
                    lastFailedAt = it[UserTotpTable.lastFailedAt]?.toKotlin(),
                )
            }
    }

    /**
     * Starts (or restarts) enrolment with a new secret. Never touches an enabled
     * authenticator; the caller checks for one first. The failure count carries
     * over, so restarting enrolment is not a way to reset the lockout.
     */
    suspend fun savePending(userId: UUID, secretEncrypted: String): Unit = dbQuery {
        val updated = UserTotpTable.update({ (UserTotpTable.userId eq userId) and UserTotpTable.enabledAt.isNull() }) {
            it[UserTotpTable.secretEncrypted] = secretEncrypted
            it[lastUsedStep] = 0
        }
        if (updated == 0) {
            UserTotpTable.insert {
                it[UserTotpTable.userId] = userId
                it[UserTotpTable.secretEncrypted] = secretEncrypted
                it[createdAt] = dbNow().toJava()
            }
        }
    }

    suspend fun enable(userId: UUID, recoveryCodeHashes: List<String>): Unit = dbQuery {
        val now = dbNow().toJava()
        UserTotpTable.update({ UserTotpTable.userId eq userId }) { it[enabledAt] = now }
        TotpRecoveryCodesTable.deleteWhere { TotpRecoveryCodesTable.userId eq userId }
        TotpRecoveryCodesTable.batchInsert(recoveryCodeHashes) { hash ->
            this[TotpRecoveryCodesTable.id] = UUID.randomUUID()
            this[TotpRecoveryCodesTable.userId] = userId
            this[TotpRecoveryCodesTable.codeHash] = hash
            this[TotpRecoveryCodesTable.createdAt] = now
        }
    }

    suspend fun disable(userId: UUID): Unit = dbQuery {
        UserTotpTable.deleteWhere { UserTotpTable.userId eq userId }
        TotpRecoveryCodesTable.deleteWhere { TotpRecoveryCodesTable.userId eq userId }
        TwoFactorChallengesTable.deleteWhere { TwoFactorChallengesTable.userId eq userId }
    }

    /**
     * Records an accepted authenticator code. Conditional on the step moving forward,
     * so two requests racing with the same code cannot both succeed.
     */
    suspend fun acceptStep(userId: UUID, step: Long): Boolean = dbQuery {
        UserTotpTable.update({ (UserTotpTable.userId eq userId) and (UserTotpTable.lastUsedStep less step) }) {
            it[lastUsedStep] = step
            it[failedAttempts] = 0
            it[lastFailedAt] = null
        } > 0
    }

    suspend fun resetFailures(userId: UUID): Unit = dbQuery {
        UserTotpTable.update({ UserTotpTable.userId eq userId }) {
            it[failedAttempts] = 0
            it[lastFailedAt] = null
        }
    }

    suspend fun recordFailure(userId: UUID): Unit = dbQuery {
        UserTotpTable.update({ UserTotpTable.userId eq userId }) {
            it[failedAttempts] = failedAttempts + 1
            it[lastFailedAt] = dbNow().toJava()
        }
    }

    /** Marks one unused recovery code as used; false if there was no such code. */
    suspend fun useRecoveryCode(userId: UUID, codeHash: String): Boolean = dbQuery {
        TotpRecoveryCodesTable.update({
            (TotpRecoveryCodesTable.userId eq userId) and
                (TotpRecoveryCodesTable.codeHash eq codeHash) and
                TotpRecoveryCodesTable.usedAt.isNull()
        }) {
            it[usedAt] = dbNow().toJava()
        } > 0
    }

    suspend fun remainingRecoveryCodes(userId: UUID): Int = dbQuery {
        TotpRecoveryCodesTable.selectAll()
            .where { (TotpRecoveryCodesTable.userId eq userId) and TotpRecoveryCodesTable.usedAt.isNull() }
            .count()
            .toInt()
    }

    // --- Sign-in challenges ---------------------------------------------------

    suspend fun createChallenge(userId: UUID, tokenHash: String, deviceId: String?, expiresAt: Instant): Unit = dbQuery {
        TwoFactorChallengesTable.insert {
            it[id] = UUID.randomUUID()
            it[TwoFactorChallengesTable.userId] = userId
            it[TwoFactorChallengesTable.tokenHash] = tokenHash
            it[TwoFactorChallengesTable.deviceId] = deviceId
            it[TwoFactorChallengesTable.expiresAt] = expiresAt.toJava()
            it[createdAt] = dbNow().toJava()
        }
    }

    suspend fun findChallenge(tokenHash: String): StoredChallenge? = dbQuery {
        TwoFactorChallengesTable.selectAll()
            .where { TwoFactorChallengesTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?.let {
                StoredChallenge(
                    id = it[TwoFactorChallengesTable.id],
                    userId = it[TwoFactorChallengesTable.userId],
                    deviceId = it[TwoFactorChallengesTable.deviceId],
                    expiresAt = it[TwoFactorChallengesTable.expiresAt].toKotlin(),
                    attempts = it[TwoFactorChallengesTable.attempts],
                    consumedAt = it[TwoFactorChallengesTable.consumedAt]?.toKotlin(),
                )
            }
    }

    suspend fun recordChallengeFailure(id: UUID): Unit = dbQuery {
        TwoFactorChallengesTable.update({ TwoFactorChallengesTable.id eq id }) {
            it[attempts] = attempts + 1
        }
    }

    /** Single use: false if another request already redeemed it. */
    suspend fun consumeChallenge(id: UUID): Boolean = dbQuery {
        TwoFactorChallengesTable.update({
            (TwoFactorChallengesTable.id eq id) and TwoFactorChallengesTable.consumedAt.isNull()
        }) {
            it[consumedAt] = dbNow().toJava()
        } > 0
    }
}
