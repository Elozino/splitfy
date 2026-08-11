package com.elozino.splitify.backend.services

import com.elozino.splitify.api.dto.AppNotification
import com.elozino.splitify.api.dto.NotificationCategory
import com.elozino.splitify.api.dto.NotificationsResponse
import com.elozino.splitify.backend.schema.dbQuery
import com.elozino.splitify.backend.schema.NotificationsTable
import com.elozino.splitify.backend.schema.toJava
import com.elozino.splitify.backend.schema.toKotlin
import com.elozino.splitify.backend.utils.AppException
import java.util.UUID
import kotlin.time.Clock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

class NotificationRepository {

    suspend fun list(userId: UUID, limit: Int): List<AppNotification> = dbQuery {
        NotificationsTable.selectAll()
            .where { NotificationsTable.userId eq userId }
            .orderBy(NotificationsTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toNotification() }
    }

    suspend fun unreadCount(userId: UUID): Int = dbQuery {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and NotificationsTable.readAt.isNull() }
            .count()
            .toInt()
    }

    suspend fun create(
        userId: UUID,
        category: NotificationCategory,
        title: String,
        message: String,
    ): AppNotification = dbQuery {
        val id = UUID.randomUUID()
        val now = Clock.System.now()
        NotificationsTable.insert {
            it[NotificationsTable.id] = id
            it[NotificationsTable.userId] = userId
            it[NotificationsTable.category] = category
            it[NotificationsTable.title] = title.take(120)
            it[NotificationsTable.message] = message.take(400)
            it[createdAt] = now.toJava()
        }
        NotificationsTable.selectAll().where { NotificationsTable.id eq id }.single().toNotification()
    }

    suspend fun markRead(userId: UUID, id: UUID): Boolean = dbQuery {
        NotificationsTable.update({
            (NotificationsTable.id eq id) and
                (NotificationsTable.userId eq userId) and
                NotificationsTable.readAt.isNull()
        }) {
            it[readAt] = Clock.System.now().toJava()
        } > 0
    }

    suspend fun markAllRead(userId: UUID): Int = dbQuery {
        NotificationsTable.update({
            (NotificationsTable.userId eq userId) and NotificationsTable.readAt.isNull()
        }) {
            it[readAt] = Clock.System.now().toJava()
        }
    }
}

/**
 * In-app notifications.
 *
 * [notify] never throws: a failed notification must not roll back the payment or
 * expense that triggered it.
 */
class NotificationService(private val repository: NotificationRepository) {

    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    suspend fun list(userId: UUID, limit: Int): NotificationsResponse = NotificationsResponse(
        items = repository.list(userId, limit),
        unreadCount = repository.unreadCount(userId),
    )

    suspend fun markRead(userId: UUID, id: UUID): NotificationsResponse {
        if (!repository.markRead(userId, id)) throw AppException.NotFound("Notification")
        return list(userId, DEFAULT_LIMIT)
    }

    suspend fun markAllRead(userId: UUID): NotificationsResponse {
        repository.markAllRead(userId)
        return list(userId, DEFAULT_LIMIT)
    }

    suspend fun notify(
        userId: UUID,
        category: NotificationCategory,
        title: String,
        message: String,
    ) {
        runCatching { repository.create(userId, category, title, message) }
            .onFailure { logger.warn("Could not store notification for {}: {}", userId, it.message) }
    }

    /** Called once at sign-up so the notification screen is never empty on day one. */
    suspend fun welcome(userId: UUID, displayName: String) {
        notify(
            userId = userId,
            category = NotificationCategory.SYSTEM,
            title = "Welcome to Splitfy, $displayName!",
            message = "Create a group, add an expense, and we will keep everyone's balance straight.",
        )
        notify(
            userId = userId,
            category = NotificationCategory.SECURITY,
            title = "Keep your account secure",
            message = "Use a unique password and sign out of devices you no longer use.",
        )
    }

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

private fun ResultRow.toNotification() = AppNotification(
    id = this[NotificationsTable.id].toString(),
    category = this[NotificationsTable.category],
    title = this[NotificationsTable.title],
    message = this[NotificationsTable.message],
    isRead = this[NotificationsTable.readAt] != null,
    createdAt = this[NotificationsTable.createdAt].toKotlin(),
)
