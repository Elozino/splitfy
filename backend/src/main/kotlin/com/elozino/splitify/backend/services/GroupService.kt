package com.elozino.splitify.backend.services

import com.elozino.splitify.api.ApiErrorCode
import com.elozino.splitify.api.dto.AddMemberRequest
import com.elozino.splitify.api.dto.CreateGroupRequest
import com.elozino.splitify.api.dto.GroupBalancesResponse
import com.elozino.splitify.api.dto.GroupSummary
import com.elozino.splitify.api.dto.UpdateGroupRequest
import com.elozino.splitify.backend.repositories.ExpenseRepository
import com.elozino.splitify.backend.repositories.GroupRepository
import com.elozino.splitify.backend.repositories.SettlementRepository
import com.elozino.splitify.backend.repositories.UserRepository
import com.elozino.splitify.backend.utils.AppException
import com.elozino.splitify.domain.model.Group
import com.elozino.splitify.domain.model.GroupRole
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.UserId
import com.elozino.splitify.domain.split.BalanceCalculator
import com.elozino.splitify.validation.Validators
import java.util.UUID

class GroupService(
    private val groups: GroupRepository,
    private val users: UserRepository,
    private val expenses: ExpenseRepository,
    private val settlements: SettlementRepository,
) {

    suspend fun create(request: CreateGroupRequest, callerId: UUID): Group {
        Validators.createGroup(request).let { if (it.isNotEmpty()) throw AppException.Validation(it) }

        // Reject unknown members up front rather than failing on a foreign key.
        val requestedIds = request.memberIds.map { UUID.fromString(it.value) }.toSet()
        if (requestedIds.isNotEmpty()) {
            val found = users.findAllByIds(requestedIds).map { UUID.fromString(it.id.value) }.toSet()
            (requestedIds - found).firstOrNull()?.let { throw AppException.NotFound("User $it") }
        }
        return groups.create(request, callerId)
    }

    suspend fun get(groupId: UUID, callerId: UUID): Group = requireMembership(groupId, callerId).first

    suspend fun list(callerId: UUID, includeArchived: Boolean): List<GroupSummary> {
        val userId = UserId(callerId.toString())
        return groups.listForUser(callerId, includeArchived).map { group ->
            val groupUuid = UUID.fromString(group.id.value)
            val ledger = expenses.allForGroup(groupUuid)
            val balances = BalanceCalculator.balances(
                currency = group.currency,
                expenses = ledger,
                settlements = settlements.listForGroup(groupUuid),
            )
            GroupSummary(
                group = group,
                totalSpend = Money(ledger.sumOf { it.total.amount }, group.currency),
                yourBalance = BalanceCalculator.balanceOf(userId, balances, group.currency),
                expenseCount = ledger.size,
            )
        }
    }

    suspend fun update(groupId: UUID, request: UpdateGroupRequest, callerId: UUID): Group {
        requireManager(groupId, callerId)
        request.name?.let { name ->
            Validators.displayName(name, "name")?.let { throw AppException.Validation(listOf(it)) }
        }
        return groups.update(
            id = groupId,
            name = request.name,
            description = request.description,
            category = request.category,
            coverImageUrl = request.coverImageUrl,
        ) ?: throw AppException.NotFound("Group")
    }

    suspend fun setArchived(groupId: UUID, archived: Boolean, callerId: UUID): Group {
        requireManager(groupId, callerId)
        return groups.setArchived(groupId, archived) ?: throw AppException.NotFound("Group")
    }

    suspend fun addMember(groupId: UUID, request: AddMemberRequest, callerId: UUID): Group {
        requireManager(groupId, callerId)

        val requestedId = request.userId
        val requestedEmail = request.email
        val user = when {
            requestedId != null -> users.findById(UUID.fromString(requestedId.value))
            requestedEmail != null -> users.findByEmail(requestedEmail)?.user
            else -> throw AppException.BadRequest("Provide either a userId or an email")
        } ?: throw AppException.NotFound("User")

        val added = groups.addMember(groupId, UUID.fromString(user.id.value), request.role)
        if (!added) throw AppException.Conflict("Already a member of this group", ApiErrorCode.ALREADY_MEMBER)

        return groups.findById(groupId) ?: throw AppException.NotFound("Group")
    }

    suspend fun removeMember(groupId: UUID, memberId: UUID, callerId: UUID): Group {
        val (group, callerRole) = requireMembership(groupId, callerId)

        // Members may remove themselves; removing anyone else needs admin rights.
        if (memberId != callerId && callerRole !in MANAGER_ROLES) {
            throw AppException.Forbidden("Only an owner or admin can remove other members")
        }
        if (groups.roleOf(groupId, memberId) == GroupRole.OWNER) {
            throw AppException.Conflict("The group owner cannot be removed")
        }

        // Leaving with an unsettled balance would silently orphan the debt.
        val balances = BalanceCalculator.balances(
            currency = group.currency,
            expenses = expenses.allForGroup(groupId),
            settlements = settlements.listForGroup(groupId),
        )
        val outstanding = balances.firstOrNull { it.userId.value == memberId.toString() }
        if (outstanding != null && !outstanding.net.isZero) {
            throw AppException.Conflict("Settle up before leaving: balance is ${outstanding.net.toPlainString()}")
        }

        groups.removeMember(groupId, memberId)
        return groups.findById(groupId) ?: throw AppException.NotFound("Group")
    }

    suspend fun balances(groupId: UUID, callerId: UUID): GroupBalancesResponse {
        val (group, _) = requireMembership(groupId, callerId)
        val balances = BalanceCalculator.balances(
            currency = group.currency,
            expenses = expenses.allForGroup(groupId),
            settlements = settlements.listForGroup(groupId),
        )
        return GroupBalancesResponse(
            groupId = group.id.value,
            currency = group.currency,
            balances = balances,
            suggestedTransfers = BalanceCalculator.simplify(balances),
        )
    }

    /**
     * The single authorization gate for group-scoped data.
     *
     * A non-member gets 404 rather than 403: confirming that a group id exists is
     * itself a leak.
     */
    suspend fun requireMembership(groupId: UUID, callerId: UUID): Pair<Group, GroupRole> {
        val group = groups.findById(groupId) ?: throw AppException.NotFound("Group")
        val role = groups.roleOf(groupId, callerId) ?: throw AppException.NotFound("Group")
        return group to role
    }

    private suspend fun requireManager(groupId: UUID, callerId: UUID): Group {
        val (group, role) = requireMembership(groupId, callerId)
        if (role !in MANAGER_ROLES) {
            throw AppException.Forbidden("Only an owner or admin can change this group")
        }
        return group
    }

    private companion object {
        val MANAGER_ROLES = setOf(GroupRole.OWNER, GroupRole.ADMIN)
    }
}
