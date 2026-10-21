package com.elozino.splitfy.backend.schema

import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.GroupCategory
import com.elozino.splitfy.domain.model.GroupRole
import com.elozino.splitfy.domain.model.SettlementStatus
import com.elozino.splitfy.domain.model.SplitMethod
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definitions. The schema itself is owned by the Flyway migrations in
 * `resources/db/migration`; these objects are the typed read/write view of it.
 *
 * Monetary columns are `BIGINT` minor units with the currency stored alongside —
 * never `FLOAT`/`DOUBLE`.
 */
object UsersTable : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 254)
    val passwordHash = varchar("password_hash", 100)
    val displayName = varchar("display_name", 60)
    val phoneNumber = varchar("phone_number", 20).nullable()
    val avatarUrl = text("avatar_url").nullable()
    val defaultCurrency = char("default_currency", 3)
    val emailVerifiedAt = timestamp("email_verified_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    /** Set when the account was deleted; the row stays as an anonymous tombstone. */
    val deletedAt = timestamp("deleted_at").nullable()
    /** False while the password hash is a random one nobody knows (Google/Apple sign-ups). */
    val hasPassword = bool("has_password").default(true)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_users_email", email)
    }
}

object RefreshTokensTable : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)

    /** Only the SHA-256 of the token is stored: a database leak cannot mint sessions. */
    val tokenHash = varchar("token_hash", 64)
    val deviceId = varchar("device_id", 128).nullable()
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_refresh_tokens_hash", tokenHash)
        index("ix_refresh_tokens_user", false, userId)
    }
}

/** A hashed one-time code sent to a user's email, with expiry and a count of wrong guesses. */
abstract class OneTimeCodesTable(name: String) : Table(name) {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val codeHash = varchar("code_hash", 64)
    val expiresAt = timestamp("expires_at")
    val consumedAt = timestamp("consumed_at").nullable()
    val attempts = integer("attempts").default(0)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_${name}_user", false, userId)
    }
}

object PasswordResetsTable : OneTimeCodesTable("password_resets")

object GroupInvitesTable : Table("group_invites") {
    val id = uuid("id")
    val groupId = uuid("group_id").references(GroupsTable.id, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 64)
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val useCount = integer("use_count").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_group_invites_token", token)
        index("ix_group_invites_group", false, groupId)
    }
}

object UserIdentitiesTable : Table("user_identities") {
    val provider = varchar("provider", 20)
    val subject = varchar("subject", 255)
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val email = varchar("email", 254).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(provider, subject)

    init {
        index("ix_user_identities_user", false, userId)
    }
}

object EmailVerificationsTable : OneTimeCodesTable("email_verifications")

/** A code sent to [newEmail]; redeeming it moves the account to that address. */
object EmailChangesTable : OneTimeCodesTable("email_changes") {
    val newEmail = varchar("new_email", 254)
}

object GroupsTable : Table("groups") {
    val id = uuid("id")
    val name = varchar("name", 60)
    val description = text("description").nullable()
    val category = enumerationByName("category", 20, GroupCategory::class)
    val currency = char("currency", 3)
    val coverImageUrl = text("cover_image_url").nullable()
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object GroupMembersTable : Table("group_members") {
    val groupId = uuid("group_id").references(GroupsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val role = enumerationByName("role", 10, GroupRole::class)
    val joinedAt = timestamp("joined_at")

    override val primaryKey = PrimaryKey(groupId, userId)

    init {
        index("ix_group_members_user", false, userId)
    }
}

object ExpensesTable : Table("expenses") {
    val id = uuid("id")
    val groupId = uuid("group_id").references(GroupsTable.id, onDelete = ReferenceOption.CASCADE)
    val description = varchar("description", 140)
    val amount = long("amount")
    val currency = char("currency", 3)
    val category = enumerationByName("category", 20, ExpenseCategory::class)
    val paidBy = uuid("paid_by").references(UsersTable.id)
    val splitMethod = enumerationByName("split_method", 12, SplitMethod::class)
    val receiptUrl = text("receipt_url").nullable()
    val notes = text("notes").nullable()
    val incurredAt = timestamp("incurred_at")
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()

    /** Idempotency key: a retried create returns the original row instead of duplicating. */
    val clientRequestId = varchar("client_request_id", 64).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_expenses_group_created", false, groupId, createdAt)
        uniqueIndex("uq_expenses_group_client_request", groupId, clientRequestId)
    }
}

object ExpenseSharesTable : Table("expense_shares") {
    val id = uuid("id")
    val expenseId = uuid("expense_id").references(ExpensesTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(UsersTable.id)
    val amount = long("amount")
    val weight = long("weight").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_expense_shares_expense_user", expenseId, userId)
    }
}

object SettlementsTable : Table("settlements") {
    val id = uuid("id")
    val groupId = uuid("group_id").references(GroupsTable.id, onDelete = ReferenceOption.CASCADE)
    val fromUserId = uuid("from_user_id").references(UsersTable.id)
    val toUserId = uuid("to_user_id").references(UsersTable.id)
    val amount = long("amount")
    val currency = char("currency", 3)
    val status = enumerationByName("status", 12, SettlementStatus::class)
    val note = text("note").nullable()
    val settledAt = timestamp("settled_at")
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = timestamp("created_at")
    val clientRequestId = varchar("client_request_id", 64).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_settlements_group", false, groupId)
        uniqueIndex("uq_settlements_group_client_request", groupId, clientRequestId)
    }
}

object ActivityLogTable : Table("activity_log") {
    val id = uuid("id")
    val groupId = uuid("group_id").references(GroupsTable.id, onDelete = ReferenceOption.CASCADE).nullable()
    val actorId = uuid("actor_id").references(UsersTable.id)
    val type = varchar("type", 32)
    val summary = varchar("summary", 280)
    val occurredAt = timestamp("occurred_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_activity_group_time", false, groupId, occurredAt)
    }
}
