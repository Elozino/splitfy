package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.backend.schema.GroupInvitesTable
import com.elozino.splitfy.backend.schema.GroupsTable
import com.elozino.splitfy.backend.schema.GroupMembersTable
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.backend.utils.TokenGenerator
import com.elozino.splitfy.domain.model.GroupRole
import kotlin.time.Duration.Companion.days
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.schema.toKotlin
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

data class InviteRecord(
    val id: UUID,
    val groupId: UUID,
    val token: String,
    val createdBy: UUID,
    val expiresAt: Instant,
    val revoked: Boolean,
) {
    fun isUsable(now: Instant) = !revoked && now < expiresAt
}

class GroupInviteRepository {

    /** Serialize link creation/reset with joins and archive updates on the group row. */
    suspend fun current(groupId: UUID, callerId: UUID, reset: Boolean): InviteRecord = dbQuery {
        lockOpenGroup(groupId)
        val member = GroupMembersTable.selectAll().where {
            (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq callerId)
        }.singleOrNull() ?: throw AppException.NotFound("Group")
        if (reset && member[GroupMembersTable.role] !in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
            throw AppException.Forbidden("Only an owner or admin can reset the invite link")
        }
        val now = Clock.System.now()
        val active = GroupInvitesTable.selectAll()
            .where { (GroupInvitesTable.groupId eq groupId) and GroupInvitesTable.revokedAt.isNull() }
            .orderBy(GroupInvitesTable.createdAt, SortOrder.DESC)
            .map { it.toInvite() }.firstOrNull { it.isUsable(now) }
        if (!reset && active != null) return@dbQuery active
        GroupInvitesTable.update({ (GroupInvitesTable.groupId eq groupId) and GroupInvitesTable.revokedAt.isNull() }) {
            it[revokedAt] = now.toJava()
        }
        val id = UUID.randomUUID()
        val token = TokenGenerator.opaqueToken(16)
        val expires = now + 30.days
        GroupInvitesTable.insert {
            it[GroupInvitesTable.id] = id
            it[GroupInvitesTable.groupId] = groupId
            it[createdBy] = callerId
            it[GroupInvitesTable.token] = token
            it[createdAt] = now.toJava()
            it[expiresAt] = expires.toJava()
        }
        InviteRecord(id, groupId, token, callerId, expires, revoked = false)
    }

    /** Membership and use count commit together; repeated or concurrent accepts are harmless. */
    suspend fun join(invite: InviteRecord, userId: UUID): Boolean = dbQuery {
        lockOpenGroup(invite.groupId)
        val current = GroupInvitesTable.selectAll().where { GroupInvitesTable.id eq invite.id }
            .singleOrNull()?.toInvite()?.takeIf { it.isUsable(Clock.System.now()) }
            ?: throw AppException.NotFound("This invite link has expired or was reset. Ask for a new one.")
        val exists = GroupMembersTable.selectAll().where {
            (GroupMembersTable.groupId eq current.groupId) and (GroupMembersTable.userId eq userId)
        }.any()
        if (exists) return@dbQuery false
        GroupMembersTable.insert {
            it[groupId] = current.groupId
            it[GroupMembersTable.userId] = userId
            it[role] = GroupRole.MEMBER
            it[joinedAt] = Clock.System.now().toJava()
        }
        GroupInvitesTable.update({ GroupInvitesTable.id eq current.id }) {
            it[useCount] = useCount + 1
        }
        true
    }

    private fun lockOpenGroup(groupId: UUID) {
        // Updating the row also forces concurrent REPEATABLE_READ transactions to
        // retry with a fresh snapshot, rather than reuse a link revoked while waiting.
        val changed = GroupsTable.update({ (GroupsTable.id eq groupId) and GroupsTable.archivedAt.isNull() }) {
            it[updatedAt] = Clock.System.now().toJava()
        }
        if (changed == 0) throw AppException.NotFound("This group is no longer available")
    }

    suspend fun findByToken(token: String): InviteRecord? = dbQuery {
        GroupInvitesTable.selectAll()
            .where { GroupInvitesTable.token eq token }
            .singleOrNull()
            ?.toInvite()
    }

    private fun ResultRow.toInvite() = InviteRecord(
        id = this[GroupInvitesTable.id],
        groupId = this[GroupInvitesTable.groupId],
        token = this[GroupInvitesTable.token],
        createdBy = this[GroupInvitesTable.createdBy],
        expiresAt = this[GroupInvitesTable.expiresAt].toKotlin(),
        revoked = this[GroupInvitesTable.revokedAt] != null,
    )
}
