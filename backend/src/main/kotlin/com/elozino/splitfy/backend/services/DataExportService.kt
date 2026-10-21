package com.elozino.splitfy.backend.services

import com.elozino.splitfy.backend.repositories.UserIdentityRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.schema.ExpenseSharesTable
import com.elozino.splitfy.backend.schema.ExpensesTable
import com.elozino.splitfy.backend.schema.GroupMembersTable
import com.elozino.splitfy.backend.schema.GroupsTable
import com.elozino.splitfy.backend.schema.NotificationsTable
import com.elozino.splitfy.backend.schema.SettlementsTable
import com.elozino.splitfy.backend.schema.UsersTable
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toKotlin
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Money
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

/**
 * "Download my data": everything the server holds about one user, as one JSON file.
 *
 * It covers what is the user's own (profile, sign-in methods, notifications) and the
 * group records they are part of, from their side: an expense appears if they paid
 * it or have a share in it, a settlement if they paid or received it. Other members
 * appear by display name only — their emails and phone numbers are theirs to export.
 * Credentials (password hash, tokens, codes) are never included.
 *
 * Returned directly rather than emailed as a link: the file is small (text only, no
 * receipt images), the app hands it straight to the share sheet, and nothing has to
 * be stored or expire on the server.
 */
class DataExportService(
    private val users: UserRepository,
    private val identities: UserIdentityRepository,
    private val clock: Clock = Clock.System,
) {

    suspend fun export(userId: UUID): DataExport {
        val user = users.findById(userId) ?: throw AppException.NotFound("User")
        val linked = identities.listForUser(userId)
        val hasPassword = users.hasPassword(userId)

        return dbQuery {
            val memberships = GroupMembersTable
                .join(GroupsTable, JoinType.INNER, GroupMembersTable.groupId, GroupsTable.id)
                .selectAll()
                .where { GroupMembersTable.userId eq userId }
                .orderBy(GroupMembersTable.joinedAt to SortOrder.ASC)
                .map { row ->
                    ExportedGroup(
                        id = row[GroupsTable.id].toString(),
                        name = row[GroupsTable.name],
                        currency = row[GroupsTable.currency],
                        role = row[GroupMembersTable.role].name,
                        joinedAt = row[GroupMembersTable.joinedAt].toKotlin(),
                        archived = row[GroupsTable.archivedAt] != null,
                    )
                }
            val groupNames = memberships.associate { it.id to it.name }

            // Every expense the user paid for or has a share of, with their share.
            val shareOf = ExpenseSharesTable.selectAll()
                .where { ExpenseSharesTable.userId eq userId }
                .associate { it[ExpenseSharesTable.expenseId] to it[ExpenseSharesTable.amount] }
            val expenseRows = ExpensesTable.selectAll()
                .where {
                    ExpensesTable.deletedAt.isNull() and
                        ((ExpensesTable.paidBy eq userId) or (ExpensesTable.id inList shareOf.keys))
                }
                .orderBy(ExpensesTable.incurredAt to SortOrder.ASC)
                .toList()
            val settlementRows = SettlementsTable.selectAll()
                .where { (SettlementsTable.fromUserId eq userId) or (SettlementsTable.toUserId eq userId) }
                .orderBy(SettlementsTable.settledAt to SortOrder.ASC)
                .toList()

            val people = (expenseRows.map { it[ExpensesTable.paidBy] } +
                settlementRows.flatMap { listOf(it[SettlementsTable.fromUserId], it[SettlementsTable.toUserId]) })
                .toSet()
            val names = if (people.isEmpty()) emptyMap() else UsersTable.selectAll()
                .where { UsersTable.id inList people }
                .associate { it[UsersTable.id] to it[UsersTable.displayName] }

            val expenses = expenseRows.map { row ->
                val id = row[ExpensesTable.id]
                val currency = CurrencyCode(row[ExpensesTable.currency])
                ExportedExpense(
                    id = id.toString(),
                    groupId = row[ExpensesTable.groupId].toString(),
                    groupName = groupNames[row[ExpensesTable.groupId].toString()],
                    description = row[ExpensesTable.description],
                    category = row[ExpensesTable.category].name,
                    amount = Money(row[ExpensesTable.amount], currency).toPlainString(),
                    yourShare = Money(shareOf[id] ?: 0L, currency).toPlainString(),
                    currency = currency.code,
                    paidByYou = row[ExpensesTable.paidBy] == userId,
                    paidBy = names[row[ExpensesTable.paidBy]],
                    notes = row[ExpensesTable.notes],
                    incurredAt = row[ExpensesTable.incurredAt].toKotlin(),
                )
            }

            val settlements = settlementRows.map { row ->
                val paid = row[SettlementsTable.fromUserId] == userId
                val other = if (paid) row[SettlementsTable.toUserId] else row[SettlementsTable.fromUserId]
                val currency = CurrencyCode(row[SettlementsTable.currency])
                ExportedSettlement(
                    id = row[SettlementsTable.id].toString(),
                    groupId = row[SettlementsTable.groupId].toString(),
                    groupName = groupNames[row[SettlementsTable.groupId].toString()],
                    direction = if (paid) "PAID" else "RECEIVED",
                    counterparty = names[other],
                    amount = Money(row[SettlementsTable.amount], currency).toPlainString(),
                    currency = currency.code,
                    status = row[SettlementsTable.status].name,
                    note = row[SettlementsTable.note],
                    settledAt = row[SettlementsTable.settledAt].toKotlin(),
                )
            }

            val notifications = NotificationsTable.selectAll()
                .where { NotificationsTable.userId eq userId }
                .orderBy(NotificationsTable.createdAt to SortOrder.ASC)
                .map { row ->
                    ExportedNotification(
                        category = row[NotificationsTable.category].name,
                        title = row[NotificationsTable.title],
                        message = row[NotificationsTable.message],
                        read = row[NotificationsTable.readAt] != null,
                        createdAt = row[NotificationsTable.createdAt].toKotlin(),
                    )
                }

            DataExport(
                exportedAt = clock.now(),
                profile = ExportedProfile(
                    id = user.id.value,
                    email = user.email,
                    emailVerified = user.emailVerified,
                    displayName = user.displayName,
                    phoneNumber = user.phoneNumber,
                    defaultCurrency = user.defaultCurrency.code,
                    hasPassword = hasPassword,
                    createdAt = user.createdAt,
                ),
                linkedAccounts = linked.map { ExportedIdentity(it.provider.name, it.email, it.linkedAt) },
                groups = memberships,
                expenses = expenses,
                settlements = settlements,
                notifications = notifications,
            )
        }
    }
}

/** The export file. [format] versions the layout for anyone parsing it. */
@Serializable
data class DataExport(
    val format: String = "splitfy-export/1",
    val exportedAt: Instant,
    val profile: ExportedProfile,
    val linkedAccounts: List<ExportedIdentity>,
    val groups: List<ExportedGroup>,
    val expenses: List<ExportedExpense>,
    val settlements: List<ExportedSettlement>,
    val notifications: List<ExportedNotification>,
)

@Serializable
data class ExportedProfile(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String,
    val phoneNumber: String?,
    val defaultCurrency: String,
    val hasPassword: Boolean,
    val createdAt: Instant,
)

@Serializable
data class ExportedIdentity(val provider: String, val email: String?, val linkedAt: Instant)

@Serializable
data class ExportedGroup(
    val id: String,
    val name: String,
    val currency: String,
    val role: String,
    val joinedAt: Instant,
    val archived: Boolean,
)

/** Amounts are decimal strings in [currency], e.g. "1250.00". */
@Serializable
data class ExportedExpense(
    val id: String,
    val groupId: String,
    val groupName: String?,
    val description: String,
    val category: String,
    val amount: String,
    val yourShare: String,
    val currency: String,
    val paidByYou: Boolean,
    val paidBy: String?,
    val notes: String?,
    val incurredAt: Instant,
)

@Serializable
data class ExportedSettlement(
    val id: String,
    val groupId: String,
    val groupName: String?,
    /** PAID when the user sent the money, RECEIVED when they got it. */
    val direction: String,
    val counterparty: String?,
    val amount: String,
    val currency: String,
    val status: String,
    val note: String?,
    val settledAt: Instant,
)

@Serializable
data class ExportedNotification(
    val category: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val createdAt: Instant,
)
