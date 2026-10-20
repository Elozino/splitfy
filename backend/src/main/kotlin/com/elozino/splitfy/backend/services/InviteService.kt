package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.dto.GroupInvite
import com.elozino.splitfy.api.dto.InvitePreview
import com.elozino.splitfy.api.dto.NotificationCategory
import com.elozino.splitfy.api.dto.PushData
import com.elozino.splitfy.backend.repositories.GroupInviteRepository
import com.elozino.splitfy.backend.repositories.GroupRepository
import com.elozino.splitfy.backend.repositories.InviteRecord
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupRole
import java.util.UUID
import kotlin.time.Clock

/**
 * Invite links. Any member may share the group's link, the same way any member can
 * add an expense; only owners and admins can reset it, which revokes the old one.
 */
class InviteService(
    private val invites: GroupInviteRepository,
    private val groups: GroupRepository,
    private val users: UserRepository,
    private val groupService: GroupService,
    private val notifications: NotificationService,
    private val publicBaseUrl: String,
) {

    /** The group's current link, minted on first request and after expiry. */
    suspend fun current(groupId: UUID, callerId: UUID): GroupInvite {
        val (group, _) = groupService.requireMembership(groupId, callerId)
        requireOpen(group)
        return invites.current(groupId, callerId, reset = false).toDto()
    }

    suspend fun reset(groupId: UUID, callerId: UUID): GroupInvite {
        val (group, role) = groupService.requireMembership(groupId, callerId)
        if (role != GroupRole.OWNER && role != GroupRole.ADMIN) {
            throw AppException.Forbidden("Only an owner or admin can reset the invite link")
        }
        requireOpen(group)
        return invites.current(groupId, callerId, reset = true).toDto()
    }

    /** Public: anyone holding the link sees the group's name and size, nothing more. */
    suspend fun preview(token: String): InvitePreview {
        val (invite, group) = usable(token)
        val inviter = users.findById(invite.createdBy)?.displayName ?: "A member"
        return InvitePreview(
            groupId = group.id.value,
            groupName = group.name,
            memberCount = group.members.size,
            invitedBy = inviter,
            currency = group.currency,
        )
    }

    /** Joins the caller to the group. Idempotent: a member accepting again just gets the group. */
    suspend fun accept(token: String, callerId: UUID): Group {
        val (invite, group) = usable(token)
        val joined = invites.join(invite, callerId)

        val joiner = users.findById(callerId)?.displayName ?: "Someone"
        if (joined && invite.createdBy != callerId) {
            notifications.notify(
                userId = invite.createdBy,
                category = NotificationCategory.GROUP,
                title = "$joiner joined ${group.name}",
                message = "They joined with your invite link.",
                data = mapOf(PushData.GROUP_ID to group.id.value, PushData.TYPE to "group"),
            )
        }
        return groups.findById(invite.groupId) ?: throw AppException.NotFound("Group")
    }

    private suspend fun usable(token: String): Pair<InviteRecord, Group> {
        val invite = invites.findByToken(token)?.takeIf { it.isUsable(Clock.System.now()) }
            ?: throw AppException.NotFound("This invite link has expired or was reset. Ask for a new one.")
        val group = groups.findById(invite.groupId)?.takeIf { !it.isArchived }
            ?: throw AppException.NotFound("This group is no longer available")
        return invite to group
    }

    private fun requireOpen(group: Group) {
        if (group.isArchived) throw AppException.Conflict("Archived groups can't take new members")
    }

    private fun InviteRecord.toDto() = GroupInvite(token = token, url = "$publicBaseUrl/i/$token", expiresAt = expiresAt)

}
