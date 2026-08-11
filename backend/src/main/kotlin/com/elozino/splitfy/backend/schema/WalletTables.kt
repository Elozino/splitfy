package com.elozino.splitfy.backend.schema

import com.elozino.splitfy.api.dto.NotificationCategory
import com.elozino.splitfy.domain.model.LedgerEntryType
import com.elozino.splitfy.domain.model.PaymentMethodType
import com.elozino.splitfy.domain.model.TransferKind
import com.elozino.splitfy.domain.model.TransferStatus
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object WalletsTable : Table("wallets") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val currency = char("currency", 3)
    val balance = long("balance").default(0)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object PaymentMethodsTable : Table("payment_methods") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val type = enumerationByName("type", 16, PaymentMethodType::class)
    val label = varchar("label", 80)
    val brand = varchar("brand", 32).nullable()
    val last4 = char("last4", 4).nullable()

    /** Opaque provider handle. A raw card number never reaches this table. */
    val providerToken = varchar("provider_token", 255)
    val isDefault = bool("is_default").default(false)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_payment_methods_user", false, userId)
    }
}

object TransfersTable : Table("transfers") {
    val id = uuid("id")
    val kind = enumerationByName("kind", 8, TransferKind::class)
    val status = enumerationByName("status", 10, TransferStatus::class)
    val fromUserId = uuid("from_user_id").references(UsersTable.id)
    val toUserId = uuid("to_user_id").references(UsersTable.id)
    val amount = long("amount")
    val currency = char("currency", 3)
    val note = text("note").nullable()
    val groupId = uuid("group_id").references(GroupsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = timestamp("created_at")
    val completedAt = timestamp("completed_at").nullable()
    val clientRequestId = varchar("client_request_id", 64).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_transfers_from", false, fromUserId, createdAt)
        index("ix_transfers_to", false, toUserId, createdAt)
        uniqueIndex("uq_transfers_creator_client_request", createdBy, clientRequestId)
    }
}

/**
 * Append-only money ledger. Rows are never updated or deleted: the balance in
 * [WalletsTable] is a cached fold of this table, and any dispute is settled by
 * replaying it.
 */
object LedgerEntriesTable : Table("ledger_entries") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val type = enumerationByName("type", 20, LedgerEntryType::class)
    val amount = long("amount")
    val currency = char("currency", 3)
    val balanceAfter = long("balance_after")
    val description = varchar("description", 140)
    val counterpartyId = uuid("counterparty_id").references(UsersTable.id).nullable()
    val paymentMethodId = uuid("payment_method_id")
        .references(PaymentMethodsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val transferId = uuid("transfer_id")
        .references(TransfersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val note = text("note").nullable()
    val occurredAt = timestamp("occurred_at")
    val clientRequestId = varchar("client_request_id", 64).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_ledger_user_time", false, userId, occurredAt)
        uniqueIndex("uq_ledger_user_client_request", userId, clientRequestId)
    }
}

object NotificationsTable : Table("notifications") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val category = enumerationByName("category", 16, NotificationCategory::class)
    val title = varchar("title", 120)
    val message = varchar("message", 400)
    val readAt = timestamp("read_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_notifications_user_time", false, userId, createdAt)
    }
}

object ContactFavoritesTable : Table("contact_favorites") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val contactUserId = uuid("contact_user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId, contactUserId)
}
