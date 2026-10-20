package com.elozino.splitfy.backend.schema

import com.elozino.splitfy.api.dto.DevicePlatform
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

/** Tables from `V7__push_notifications.sql`. */
object DeviceTokensTable : Table("device_tokens") {
    val token = varchar("token", 512)
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val platform = enumerationByName("platform", 10, DevicePlatform::class)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(token)

    init {
        index("ix_device_tokens_user", false, userId)
    }
}

object NotificationPreferencesTable : Table("notification_preferences") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val newExpenses = bool("new_expenses").default(true)
    val settlements = bool("settlements").default(true)
    val groupActivity = bool("group_activity").default(true)
    val paymentReminders = bool("payment_reminders").default(true)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object JobRunsTable : Table("job_runs") {
    val job = varchar("job", 40)
    val runOn = date("run_on")
    val claimedAt = timestamp("claimed_at")

    override val primaryKey = PrimaryKey(job, runOn)
}

object PaymentRemindersTable : Table("payment_reminders") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val groupId = uuid("group_id").references(GroupsTable.id, onDelete = ReferenceOption.CASCADE)
    val lastSentAt = timestamp("last_sent_at")

    override val primaryKey = PrimaryKey(userId, groupId)
}
