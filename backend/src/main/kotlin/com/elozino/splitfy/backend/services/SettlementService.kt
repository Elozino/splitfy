package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.api.dto.NotificationCategory
import com.elozino.splitfy.api.dto.PushData
import com.elozino.splitfy.api.FieldError
import com.elozino.splitfy.backend.repositories.NewSettlement
import com.elozino.splitfy.backend.repositories.SettlementRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SettlementStatus
import com.elozino.splitfy.domain.model.UserId
import java.util.UUID
import kotlin.time.Clock

class SettlementService(
    private val settlements: SettlementRepository,
    private val groupService: GroupService,
    private val notifications: NotificationService,
    private val users: UserRepository,
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

        val created = settlements.create(
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
        val actor = nameOf(callerId)
        val amount = created.amount.display()
        if (created.fromUserId.value == callerId.toString()) {
            tell(created.toUserId, group, "$actor paid you $amount", "Confirm it in ${group.name} once it arrives.")
        } else {
            tell(created.fromUserId, group, "$actor recorded your payment", "$amount in ${group.name}, received.")
        }
        return created
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
        val confirmed = settlements.transition(settlementId, SettlementStatus.PENDING, SettlementStatus.CONFIRMED)
            ?: throw lostRace(settlementId)
        val group = groupService.get(groupId, callerId)
        tell(
            confirmed.fromUserId, group,
            "${nameOf(callerId)} confirmed your payment",
            "${confirmed.amount.display()} in ${group.name} is settled.",
        )
        return confirmed
    }

    suspend fun cancel(groupId: UUID, settlementId: UUID, callerId: UUID): Settlement {
        val settlement = load(groupId, settlementId, callerId)
        val parties = setOf(settlement.fromUserId.value, settlement.toUserId.value)
        if (callerId.toString() !in parties) {
            throw AppException.Forbidden("Only the payer or the recipient can cancel this payment")
        }
        // A retried cancel is a no-op rather than an error, and doesn't notify twice.
        if (settlement.status == SettlementStatus.CANCELLED) return settlement
        if (settlement.status == SettlementStatus.CONFIRMED) {
            throw AppException.Conflict("A confirmed settlement cannot be cancelled")
        }
        val cancelled = settlements.transition(settlementId, SettlementStatus.PENDING, SettlementStatus.CANCELLED)
            ?: return settlements.findById(settlementId)
                ?.takeIf { it.status == SettlementStatus.CANCELLED }
                ?: throw lostRace(settlementId)
        val other = if (cancelled.fromUserId.value == callerId.toString()) cancelled.toUserId else cancelled.fromUserId
        val group = groupService.get(groupId, callerId)
        tell(
            other, group,
            "${nameOf(callerId)} cancelled a payment",
            "${cancelled.amount.display()} in ${group.name} was cancelled.",
        )
        return cancelled
    }

    /** Another request changed the status between our read and our conditional write. */
    private suspend fun lostRace(settlementId: UUID): AppException {
        val current = settlements.findById(settlementId) ?: return AppException.NotFound("Settlement")
        return AppException.Conflict("This settlement is already ${current.status.name.lowercase()}")
    }

    private suspend fun nameOf(userId: UUID): String = users.findById(userId)?.displayName ?: "Someone"

    private suspend fun tell(recipient: UserId, group: Group, title: String, message: String) {
        notifications.notify(
            userId = UUID.fromString(recipient.value),
            category = NotificationCategory.PAYMENT,
            title = title,
            message = message,
            data = mapOf(PushData.GROUP_ID to group.id.value, PushData.TYPE to "settlement"),
        )
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
