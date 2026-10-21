package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.dto.NotificationCategory
import com.elozino.splitfy.api.dto.NotificationPreferences
import com.elozino.splitfy.backend.repositories.DeviceTokenRepository
import com.elozino.splitfy.backend.repositories.NotificationPreferencesRepository
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/** Which preference switch a push is gated by. */
enum class PushTopic {
    EXPENSES,
    SETTLEMENTS,
    GROUPS,
    REMINDERS,

    /** Security and account messages: always pushed. */
    ACCOUNT,

    /** In-app only, never pushed (promotions). */
    NONE;

    fun allowedBy(preferences: NotificationPreferences): Boolean = when (this) {
        EXPENSES -> preferences.newExpenses
        SETTLEMENTS -> preferences.settlements
        GROUPS -> preferences.groupActivity
        REMINDERS -> preferences.paymentReminders
        ACCOUNT -> true
        NONE -> false
    }

    companion object {
        fun defaultFor(category: NotificationCategory): PushTopic = when (category) {
            NotificationCategory.PAYMENT -> SETTLEMENTS
            NotificationCategory.GROUP -> GROUPS
            NotificationCategory.SECURITY, NotificationCategory.SYSTEM -> ACCOUNT
            NotificationCategory.PROMOTION -> NONE
        }
    }
}

/**
 * Fans a push out to every device a user has registered, off the request path.
 *
 * Sending is launched on this dispatcher's own scope, so the expense or payment that
 * triggered it neither waits for FCM nor fails because of it. Tokens FCM reports as
 * dead are deleted on the way.
 */
class PushDispatcher(
    private val devices: DeviceTokenRepository,
    private val preferences: NotificationPreferencesRepository,
    private val sender: PushSender,
) : Closeable {

    private val logger = LoggerFactory.getLogger(PushDispatcher::class.java)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(
        job + Dispatchers.IO + CoroutineExceptionHandler { _, e -> logger.warn("Push delivery failed: {}", e.message) }
    )

    fun dispatch(userId: UUID, topic: PushTopic, message: PushMessage) {
        if (topic == PushTopic.NONE) return
        scope.launch { deliver(userId, topic, message) }
    }

    /** The synchronous core of [dispatch]: returns how many devices accepted the push. */
    suspend fun deliver(userId: UUID, topic: PushTopic, message: PushMessage): Int {
        if (!topic.allowedBy(preferences.get(userId))) return 0
        var sent = 0
        for (token in devices.tokensFor(userId)) {
            when (sender.send(token, message)) {
                PushResult.SENT -> sent++
                PushResult.INVALID_TOKEN -> {
                    logger.info("Dropping a device token FCM no longer accepts")
                    devices.delete(token)
                }
                PushResult.FAILED -> Unit
            }
        }
        return sent
    }

    /** Waits for every push launched so far. For tests and graceful shutdown. */
    suspend fun drain() {
        job.children.toList().joinAll()
    }

    override fun close() = scope.cancel()
}
