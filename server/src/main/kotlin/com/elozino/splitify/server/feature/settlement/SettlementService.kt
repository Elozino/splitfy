package com.elozino.splitify.server.feature.settlement

import com.elozino.splitify.server.common.AppException
import com.elozino.splitify.server.feature.group.GroupService
import com.elozino.splitify.shared.api.ApiErrorCode
import com.elozino.splitify.shared.api.FieldError
import com.elozino.splitify.shared.api.dto.CreateSettlementRequest
import com.elozino.splitify.shared.domain.model.Group
import com.elozino.splitify.shared.domain.model.Settlement
import com.elozino.splitify.shared.domain.model.SettlementStatus
import com.elozino.splitify.shared.domain.model.UserId
import java.util.UUID
import kotlin.time.Clock

class SettlementService(
    private val settlements: SettlementRepository,
    private val groupService: GroupService,
) {

    suspend fun create(groupId: UUID, request: CreateSettlementRequest, callerId: UUID): Settlement {
        val (group, _) = groupService.requireMembership(groupId, callerId)

        request.clientRequestId?.let { key ->
            settlements.findByClientRequestId(groupId, key)?.let { return it }
        }

        if (!request.amount.isPositive) {
            throw AppException.Validation(listOf(FieldError("amount", "Amount must be greater than zero")))
        }
        if (request.amount.currency != group.currency) {
            throw AppException.BadRequest(
                "This group settles in ${group.currency.code}",
                ApiErrorCode.CURRENCY_MISMATCH,
            )
        }
        if (request.fromUserId == request.toUserId) {
            throw AppException.Validation(listOf(FieldError("toUserId", "Payer and payee must differ")))
        }
        requireMember(group, request.fromUserId, "fromUserId")
        requireMember(group, request.toUserId, "toUserId")

        // Recording a payment on someone else's behalf would let a member fabricate
        // repayments they never made.
        val callerIsParty = callerId.toString() in setOf(request.fromUserId.value, request.toUserId.value)
        if (!callerIsParty) {
            throw AppException.Forbidden("You can only record settlements you are part of")
        }

        return settlements.create(
            NewSettlement(
                groupId = groupId,
                fromUserId = UUID.fromString(request.fromUserId.value),
                toUserId = UUID.fromString(request.toUserId.value),
                amount = request.amount,
                note = request.note,
                settledAt = request.settledAt ?: Clock.System.now(),
                createdBy = callerId,
                clientRequestId = request.clientRequestId,
            )
        )
    }

    suspend fun list(groupId: UUID, callerId: UUID): List<Settlement> {
        groupService.requireMembership(groupId, callerId)
        return settlements.listForGroup(groupId)
    }

    /**
     * Only the payee can confirm: money is considered received when the person who
     * was owed says it arrived, not when the payer says they sent it.
     */
    suspend fun confirm(groupId: UUID, settlementId: UUID, callerId: UUID): Settlement {
        val settlement = load(groupId, settlementId, callerId)
        if (settlement.status != SettlementStatus.PENDING) {
            throw AppException.Conflict("This settlement is already ${settlement.status.name.lowercase()}")
        }
        if (settlement.toUserId.value != callerId.toString()) {
            throw AppException.Forbidden("Only the recipient can confirm this payment")
        }
        return settlements.updateStatus(settlementId, SettlementStatus.CONFIRMED)
            ?: throw AppException.NotFound("Settlement")
    }

    suspend fun cancel(groupId: UUID, settlementId: UUID, callerId: UUID): Settlement {
        val settlement = load(groupId, settlementId, callerId)
        if (settlement.status == SettlementStatus.CONFIRMED) {
            throw AppException.Conflict("A confirmed settlement cannot be cancelled")
        }
        val parties = setOf(settlement.fromUserId.value, settlement.toUserId.value)
        if (callerId.toString() !in parties) {
            throw AppException.Forbidden("Only the payer or the recipient can cancel this payment")
        }
        return settlements.updateStatus(settlementId, SettlementStatus.CANCELLED)
            ?: throw AppException.NotFound("Settlement")
    }

    private suspend fun load(groupId: UUID, settlementId: UUID, callerId: UUID): Settlement {
        groupService.requireMembership(groupId, callerId)
        return settlements.findById(settlementId)
            ?.takeIf { it.groupId.value == groupId.toString() }
            ?: throw AppException.NotFound("Settlement")
    }

    private fun requireMember(group: Group, userId: UserId, field: String) {
        if (group.members.none { it.userId == userId }) {
            throw AppException.Validation(listOf(FieldError(field, "Not a member of this group")))
        }
    }
}
