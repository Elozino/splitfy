package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.api.dto.DevicePlatform
import com.elozino.splitfy.api.dto.NotificationPreferences
import com.elozino.splitfy.api.dto.UpdateNotificationPreferencesRequest
import com.elozino.splitfy.backend.schema.DeviceTokensTable
import com.elozino.splitfy.backend.schema.JobRunsTable
import com.elozino.splitfy.backend.schema.NotificationPreferencesTable
import com.elozino.splitfy.backend.schema.PaymentRemindersTable
import com.elozino.splitfy.backend.schema.dbQuery
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class DeviceTokenRepository {

    /**
     * Upserts on the token: re-registering is a no-op, and a token that last belonged
     * to someone else (a shared phone, sign-out without unregister) moves to [userId].
     */
    suspend fun register(userId: UUID, token: String, platform: DevicePlatform): Unit = dbQuery {
        val now = Instant.now()
        DeviceTokensTable.upsert(
            onUpdateExclude = listOf(DeviceTokensTable.createdAt),
        ) {
            it[DeviceTokensTable.token] = token
            it[DeviceTokensTable.userId] = userId
            it[DeviceTokensTable.platform] = platform
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    /** Only the owner can remove a token, so knowing someone's token is not enough to silence them. */
    suspend fun unregister(userId: UUID, token: String): Unit = dbQuery {
        DeviceTokensTable.deleteWhere { (DeviceTokensTable.token eq token) and (DeviceTokensTable.userId eq userId) }
    }

    /** For tokens FCM reports as dead, whoever they belong to. */
    suspend fun delete(token: String): Unit = dbQuery {
        DeviceTokensTable.deleteWhere { DeviceTokensTable.token eq token }
    }

    suspend fun tokensFor(userId: UUID): List<String> = dbQuery {
        DeviceTokensTable.selectAll()
            .where { DeviceTokensTable.userId eq userId }
            .map { it[DeviceTokensTable.token] }
    }
}

class NotificationPreferencesRepository {

    suspend fun get(userId: UUID): NotificationPreferences = dbQuery { load(userId) }

    suspend fun update(userId: UUID, request: UpdateNotificationPreferencesRequest): NotificationPreferences = dbQuery {
        val current = load(userId)
        val next = NotificationPreferences(
            newExpenses = request.newExpenses ?: current.newExpenses,
            settlements = request.settlements ?: current.settlements,
            groupActivity = request.groupActivity ?: current.groupActivity,
            paymentReminders = request.paymentReminders ?: current.paymentReminders,
        )
        NotificationPreferencesTable.upsert {
            it[NotificationPreferencesTable.userId] = userId
            it[newExpenses] = next.newExpenses
            it[settlements] = next.settlements
            it[groupActivity] = next.groupActivity
            it[paymentReminders] = next.paymentReminders
            it[updatedAt] = Instant.now()
        }
        next
    }

    private fun load(userId: UUID): NotificationPreferences =
        NotificationPreferencesTable.selectAll()
            .where { NotificationPreferencesTable.userId eq userId }
            .singleOrNull()
            ?.toPreferences()
            ?: NotificationPreferences()
}

private fun ResultRow.toPreferences() = NotificationPreferences(
    newExpenses = this[NotificationPreferencesTable.newExpenses],
    settlements = this[NotificationPreferencesTable.settlements],
    groupActivity = this[NotificationPreferencesTable.groupActivity],
    paymentReminders = this[NotificationPreferencesTable.paymentReminders],
)

/** Bookkeeping for scheduled jobs that must run once per day across every instance. */
class JobRunRepository {

    /** True for exactly one caller per `(job, day)`, however many instances race for it. */
    suspend fun claim(job: String, day: LocalDate): Boolean = dbQuery {
        JobRunsTable.insertIgnore {
            it[JobRunsTable.job] = job
            it[runOn] = day
            it[claimedAt] = Instant.now()
        }.insertedCount == 1
    }
}

class PaymentReminderRepository {

    suspend fun lastSent(userId: UUID, groupId: UUID): Instant? = dbQuery {
        PaymentRemindersTable.selectAll()
            .where { (PaymentRemindersTable.userId eq userId) and (PaymentRemindersTable.groupId eq groupId) }
            .singleOrNull()
            ?.get(PaymentRemindersTable.lastSentAt)
    }

    suspend fun markSent(userId: UUID, groupId: UUID, at: Instant): Unit = dbQuery {
        PaymentRemindersTable.upsert {
            it[PaymentRemindersTable.userId] = userId
            it[PaymentRemindersTable.groupId] = groupId
            it[lastSentAt] = at
        }
    }
}
