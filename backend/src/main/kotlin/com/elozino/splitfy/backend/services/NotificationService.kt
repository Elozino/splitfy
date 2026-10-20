package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.FieldError
import com.elozino.splitfy.api.dto.AppNotification
import com.elozino.splitfy.api.dto.NotificationPreferences
import com.elozino.splitfy.api.dto.RegisterDeviceRequest
import com.elozino.splitfy.api.dto.UpdateNotificationPreferencesRequest
import com.elozino.splitfy.api.dto.NotificationCategory
import com.elozino.splitfy.api.dto.NotificationsResponse
import com.elozino.splitfy.backend.repositories.DeviceTokenRepository
import com.elozino.splitfy.backend.repositories.NotificationPreferencesRepository
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.NotificationsTable
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.schema.toKotlin
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.Money
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

    /**
     * Marks one notification read. Returns false only when the user has no such
     * notification: one that is already read is still a success, so a double tap
     * does not surface an error.
     */
    suspend fun markRead(userId: UUID, id: UUID): Boolean = dbQuery {
        val owned = (NotificationsTable.id eq id) and (NotificationsTable.userId eq userId)
        NotificationsTable.update({ owned and NotificationsTable.readAt.isNull() }) {
            it[readAt] = Clock.System.now().toJava()
        }
        NotificationsTable.selectAll().where { owned }.any()
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
 * In-app notifications, mirrored to the user's phones as push notifications.
 *
 * [notify] never throws: a failed notification must not roll back the payment or
 * expense that triggered it.
 */
class NotificationService(
    private val repository: NotificationRepository,
    private val push: PushDispatcher,
    private val devices: DeviceTokenRepository,
    private val preferences: NotificationPreferencesRepository,
) {

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

    /**
     * Stores an in-app notification and pushes it, unless the user switched [topic]
     * off. [data] rides along in the push for the app to route a tap (see `PushData`).
     */
    suspend fun notify(
        userId: UUID,
        category: NotificationCategory,
        title: String,
        message: String,
        topic: PushTopic = PushTopic.defaultFor(category),
        data: Map<String, String> = emptyMap(),
    ) {
        runCatching { repository.create(userId, category, title, message) }
            .onFailure { logger.warn("Could not store notification for {}: {}", userId, it.message) }
        runCatching { push.dispatch(userId, topic, PushMessage(title.take(120), message.take(400), data)) }
            .onFailure { logger.warn("Could not queue push for {}: {}", userId, it.message) }
    }

    suspend fun registerDevice(userId: UUID, request: RegisterDeviceRequest) {
        devices.register(userId, validToken(request.token), request.platform)
    }

    suspend fun unregisterDevice(userId: UUID, token: String) {
        devices.unregister(userId, validToken(token))
    }

    suspend fun preferences(userId: UUID): NotificationPreferences = preferences.get(userId)

    suspend fun updatePreferences(userId: UUID, request: UpdateNotificationPreferencesRequest): NotificationPreferences =
        preferences.update(userId, request)

    private fun validToken(token: String): String {
        val trimmed = token.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TOKEN_LENGTH) {
            throw AppException.Validation(listOf(FieldError("token", "Not a valid push token")))
        }
        return trimmed
    }

    /** Called once at sign-up so the notification screen is never empty on day one. */
    suspend fun welcome(userId: UUID, displayName: String) {
        notify(
            userId = userId,
            category = NotificationCategory.SYSTEM,
            title = "Welcome to splitfy, $displayName!",
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
        private const val MAX_TOKEN_LENGTH = 512
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

/** `NGN 1250.00`: unambiguous in a notification, where there is no locale to format for. */
internal fun Money.display(): String = "${currency.code} ${toPlainString()}"
