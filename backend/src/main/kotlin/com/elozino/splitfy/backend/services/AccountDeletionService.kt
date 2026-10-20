package com.elozino.splitfy.backend.services

import com.elozino.splitfy.backend.repositories.ExpenseRepository
import com.elozino.splitfy.backend.repositories.GroupRepository
import com.elozino.splitfy.backend.repositories.SettlementRepository
import com.elozino.splitfy.backend.schema.ContactFavoritesTable
import com.elozino.splitfy.backend.schema.EmailVerificationsTable
import com.elozino.splitfy.backend.schema.GroupMembersTable
import com.elozino.splitfy.backend.schema.GroupsTable
import com.elozino.splitfy.backend.schema.NotificationsTable
import com.elozino.splitfy.backend.schema.PasswordResetsTable
import com.elozino.splitfy.backend.schema.PaymentMethodsTable
import com.elozino.splitfy.backend.schema.RefreshTokensTable
import com.elozino.splitfy.backend.schema.UserIdentitiesTable
import com.elozino.splitfy.backend.schema.UsersTable
import com.elozino.splitfy.backend.schema.WalletsTable
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.GroupRole
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.domain.split.BalanceCalculator
import java.util.UUID
import kotlin.time.Clock
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Deletes an account, as both app stores require.
 *
 * The user row is anonymised rather than removed: expenses, shares and
 * settlements reference it, and deleting it would either fail on those foreign
 * keys or rewrite other people's history. What goes is everything that identifies
 * or authenticates the person — email, name, phone, avatar, password, linked
 * Google/Apple identities, sessions, one-time codes, notifications, favourites and
 * payment methods. Other members see "Deleted user" against past expenses.
 *
 * Refused while the user still owes or is owed money in any group, because the
 * remaining members could never settle with an account nobody controls.
 */
class AccountDeletionService(
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val settlements: SettlementRepository,
) {

    suspend fun delete(callerId: UUID) {
        dbQuery {
            UsersTable.selectAll()
                .where { (UsersTable.id eq callerId) and UsersTable.deletedAt.isNull() }
                .singleOrNull()
        } ?: throw AppException.NotFound("User")

        val unsettled = unsettledGroups(callerId)
        if (unsettled.isNotEmpty()) {
            throw AppException.Conflict(
                "Settle up before deleting your account. You still have a balance in: " +
                    unsettled.joinToString(", "),
            )
        }
        val walletBalance = dbQuery {
            WalletsTable.selectAll().where { WalletsTable.userId eq callerId }.singleOrNull()?.get(WalletsTable.balance)
        }
        if (walletBalance != null && walletBalance != 0L) {
            throw AppException.Conflict("Withdraw your wallet balance before deleting your account")
        }

        anonymise(callerId)
    }

    /** Names (with the amount) of every group where the caller's net balance is not zero. */
    private suspend fun unsettledGroups(callerId: UUID): List<String> {
        val userId = UserId(callerId.toString())
        return groups.listForUser(callerId, includeArchived = true).mapNotNull { group ->
            val groupId = UUID.fromString(group.id.value)
            val balances = BalanceCalculator.balances(
                currency = group.currency,
                expenses = expenses.allForGroup(groupId),
                settlements = settlements.listForGroup(groupId),
            )
            val net = BalanceCalculator.balanceOf(userId, balances, group.currency)
            if (net.isZero) null else "${group.name} (${group.currency.code} ${net.toPlainString()})"
        }
    }

    private suspend fun anonymise(callerId: UUID) = dbQuery {
        val now = Clock.System.now().toJava()

        UsersTable.update({ UsersTable.id eq callerId }) {
            // Frees the real address for a future sign-up; `.invalid` can never receive mail.
            it[email] = "deleted-$callerId@deleted.invalid"
            // Not a valid BCrypt hash, so no password can ever match it.
            it[passwordHash] = DELETED_PASSWORD_HASH
            it[displayName] = DELETED_DISPLAY_NAME
            it[phoneNumber] = null
            it[avatarUrl] = null
            it[emailVerifiedAt] = null
            it[deletedAt] = now
            it[updatedAt] = now
        }

        UserIdentitiesTable.deleteWhere { userId eq callerId }
        RefreshTokensTable.deleteWhere { userId eq callerId }
        PasswordResetsTable.deleteWhere { userId eq callerId }
        EmailVerificationsTable.deleteWhere { userId eq callerId }
        NotificationsTable.deleteWhere { userId eq callerId }
        PaymentMethodsTable.deleteWhere { userId eq callerId }
        ContactFavoritesTable.deleteWhere { (userId eq callerId) or (contactUserId eq callerId) }

        handOverOwnedGroups(callerId)
    }

    /**
     * The departed user stays a member so past expenses still render, but a group
     * they owned needs a new owner: the longest-standing remaining member. A group
     * with nobody else in it is archived.
     */
    private fun handOverOwnedGroups(callerId: UUID) {
        val owned = GroupMembersTable.selectAll()
            .where { (GroupMembersTable.userId eq callerId) and (GroupMembersTable.role eq GroupRole.OWNER) }
            .map { it[GroupMembersTable.groupId] }

        for (groupId in owned) {
            val successor = GroupMembersTable.selectAll()
                .where { (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId neq callerId) }
                .orderBy(GroupMembersTable.joinedAt to SortOrder.ASC)
                .firstOrNull { it[GroupMembersTable.userId].isLiveUser() }
                ?.get(GroupMembersTable.userId)

            GroupMembersTable.update({ (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq callerId) }) {
                it[role] = GroupRole.MEMBER
            }
            if (successor != null) {
                GroupMembersTable.update({ (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq successor) }) {
                    it[role] = GroupRole.OWNER
                }
            } else {
                GroupsTable.update({ (GroupsTable.id eq groupId) and GroupsTable.archivedAt.isNull() }) {
                    it[archivedAt] = Clock.System.now().toJava()
                }
            }
        }
    }

    private fun UUID.isLiveUser(): Boolean =
        UsersTable.selectAll().where { (UsersTable.id eq this@isLiveUser) and UsersTable.deletedAt.isNull() }.any()

    companion object {
        const val DELETED_DISPLAY_NAME = "Deleted user"
        private const val DELETED_PASSWORD_HASH = "!deleted"
    }
}
