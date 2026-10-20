package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.GroupMembersTable
import com.elozino.splitfy.backend.schema.GroupsTable
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.schema.toKotlin
import com.elozino.splitfy.backend.schema.UsersTable
import com.elozino.splitfy.backend.storage.ObjectKeys
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupCategory
import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.GroupMember
import com.elozino.splitfy.domain.model.GroupRole
import com.elozino.splitfy.domain.model.UserId
import java.util.UUID
import kotlin.time.Clock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update

/**
 * [coverUrl] turns a stored cover key into something a client can display (a public
 * or signed URL), or null when it can't be served.
 */
class GroupRepository(private val coverUrl: (String) -> String? = { null }) {

    suspend fun create(request: CreateGroupRequest, creatorId: UUID): Group = dbQuery {
        val now = Clock.System.now()
        val groupId = UUID.randomUUID()

        GroupsTable.insert {
            it[id] = groupId
            it[name] = request.name.trim()
            it[description] = request.description
            it[category] = request.category
            it[currency] = request.currency.code
            it[coverImageUrl] = request.coverImageUrl
            it[createdBy] = creatorId
            it[createdAt] = now.toJava()
            it[updatedAt] = now.toJava()
        }

        // The creator is always an OWNER, regardless of what the request said.
        val memberIds = (listOf(creatorId) + request.memberIds.map { UUID.fromString(it.value) }).distinct()
        GroupMembersTable.batchInsert(memberIds) { memberId ->
            this[GroupMembersTable.groupId] = groupId
            this[GroupMembersTable.userId] = memberId
            this[GroupMembersTable.role] = if (memberId == creatorId) GroupRole.OWNER else GroupRole.MEMBER
            this[GroupMembersTable.joinedAt] = now.toJava()
        }

        loadGroup(groupId) ?: error("Group $groupId vanished immediately after insert")
    }

    suspend fun findById(id: UUID): Group? = dbQuery { loadGroup(id) }

    suspend fun listForUser(userId: UUID, includeArchived: Boolean): List<Group> = dbQuery {
        val groupIds = GroupMembersTable
            .selectAll()
            .where { GroupMembersTable.userId eq userId }
            .map { it[GroupMembersTable.groupId] }

        if (groupIds.isEmpty()) return@dbQuery emptyList()

        val rows = GroupsTable.selectAll()
            .where {
                if (includeArchived) GroupsTable.id inList groupIds
                else (GroupsTable.id inList groupIds) and GroupsTable.archivedAt.isNull()
            }
            .orderBy(GroupsTable.updatedAt, SortOrder.DESC)
            .toList()

        val membersByGroup = loadMembers(rows.map { it[GroupsTable.id] })
        rows.map { it.toGroup(membersByGroup[it[GroupsTable.id]].orEmpty()).withCoverUrl() }
    }

    suspend fun update(
        id: UUID,
        name: String?,
        description: String?,
        category: GroupCategory?,
        coverImageUrl: String?,
    ): Group? = dbQuery {
        GroupsTable.update({ GroupsTable.id eq id }) { statement ->
            name?.let { statement[GroupsTable.name] = it.trim() }
            description?.let { statement[GroupsTable.description] = it }
            category?.let { statement[GroupsTable.category] = it }
            coverImageUrl?.let { statement[GroupsTable.coverImageUrl] = it.ifEmpty { null } }
            statement[updatedAt] = Clock.System.now().toJava()
        }
        loadGroup(id)
    }

    suspend fun setArchived(id: UUID, archived: Boolean): Group? = dbQuery {
        val now = Clock.System.now()
        GroupsTable.update({ GroupsTable.id eq id }) {
            it[archivedAt] = if (archived) now.toJava() else null
            it[updatedAt] = now.toJava()
        }
        loadGroup(id)
    }

    suspend fun addMember(groupId: UUID, userId: UUID, role: GroupRole): Boolean = dbQuery {
        val alreadyMember = GroupMembersTable.selectAll()
            .where { (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq userId) }
            .any()
        if (alreadyMember) return@dbQuery false

        GroupMembersTable.insert {
            it[GroupMembersTable.groupId] = groupId
            it[GroupMembersTable.userId] = userId
            it[GroupMembersTable.role] = role
            it[joinedAt] = Clock.System.now().toJava()
        }
        true
    }

    suspend fun removeMember(groupId: UUID, userId: UUID): Boolean = dbQuery {
        GroupMembersTable.deleteWhere {
            (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq userId)
        } > 0
    }

    suspend fun roleOf(groupId: UUID, userId: UUID): GroupRole? = dbQuery {
        GroupMembersTable.selectAll()
            .where { (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq userId) }
            .singleOrNull()
            ?.get(GroupMembersTable.role)
    }

    suspend fun memberIds(groupId: UUID): Set<UUID> = dbQuery {
        GroupMembersTable.selectAll()
            .where { GroupMembersTable.groupId eq groupId }
            .mapTo(mutableSetOf()) { it[GroupMembersTable.userId] }
    }

    // --- helpers, always called inside a transaction ------------------------

    private fun loadGroup(id: UUID): Group? {
        val row = GroupsTable.selectAll().where { GroupsTable.id eq id }.singleOrNull() ?: return null
        return row.toGroup(loadMembers(listOf(id))[id].orEmpty()).withCoverUrl()
    }

    /** Only keys this server issued are resolved; anything else stored earlier is dropped. */
    private fun Group.withCoverUrl(): Group =
        copy(coverImageUrl = coverImageUrl?.takeIf(ObjectKeys::isOurs)?.let(coverUrl))

    private fun loadMembers(groupIds: List<UUID>): Map<UUID, List<GroupMember>> {
        if (groupIds.isEmpty()) return emptyMap()
        return GroupMembersTable
            .join(UsersTable, JoinType.INNER, GroupMembersTable.userId, UsersTable.id)
            .selectAll()
            .where { GroupMembersTable.groupId inList groupIds }
            .groupBy({ it[GroupMembersTable.groupId] }) { row ->
                GroupMember(
                    userId = UserId(row[GroupMembersTable.userId].toString()),
                    displayName = row[UsersTable.displayName],
                    avatarUrl = row[UsersTable.avatarUrl],
                    role = row[GroupMembersTable.role],
                    joinedAt = row[GroupMembersTable.joinedAt].toKotlin(),
                )
            }
    }
}

private fun ResultRow.toGroup(members: List<GroupMember>) = Group(
    id = GroupId(this[GroupsTable.id].toString()),
    name = this[GroupsTable.name],
    description = this[GroupsTable.description],
    category = this[GroupsTable.category],
    currency = CurrencyCode(this[GroupsTable.currency]),
    coverImageUrl = this[GroupsTable.coverImageUrl],
    members = members,
    createdBy = UserId(this[GroupsTable.createdBy].toString()),
    createdAt = this[GroupsTable.createdAt].toKotlin(),
    archivedAt = this[GroupsTable.archivedAt]?.toKotlin(),
)
