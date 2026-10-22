package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.dto.CreateTransferRequest
import com.elozino.splitfy.api.dto.NotificationCategory
import com.elozino.splitfy.api.FieldError
import com.elozino.splitfy.backend.repositories.LedgerWrite
import com.elozino.splitfy.backend.repositories.NewTransfer
import com.elozino.splitfy.backend.repositories.TransferRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.LedgerEntryType
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Transfer
import com.elozino.splitfy.domain.model.TransferKind
import com.elozino.splitfy.domain.model.TransferStatus
import java.util.UUID
import kotlin.time.Clock

/**
 * Peer-to-peer payments and requests.
 *
 * A PAYMENT settles immediately: the sender's wallet is debited and the recipient's
 * credited in the same call. A REQUEST creates nothing but a pending record — money
 * only moves when the person being asked approves it, so nobody can pull funds from
 * someone else's wallet.
 */
class TransferService(
    private val transfers: TransferRepository,
    private val users: UserRepository,
    private val wallet: WalletService,
    private val notifications: NotificationService,
) {

    suspend fun create(callerId: UUID, request: CreateTransferRequest): Transfer {
        request.clientRequestId?.let { key ->
            transfers.findByClientRequestId(callerId, key)?.let { return it }
        }

        if (!request.amount.isPositive) {
            throw AppException.Validation(listOf(FieldError("amount", "Enter an amount greater than zero")))
        }
        val counterpartyId = runCatching { UUID.fromString(request.counterpartyId.value) }
            .getOrElse { throw AppException.BadRequest("Invalid recipient") }
        if (counterpartyId == callerId) {
            throw AppException.Validation(listOf(FieldError("counterpartyId", "Pick someone other than yourself")))
        }

        val caller = users.findById(callerId) ?: throw AppException.NotFound("User")
        val counterparty = users.findById(counterpartyId) ?: throw AppException.NotFound("Recipient")

        return when (request.kind) {
            TransferKind.PAYMENT -> {
                val transfer = transfers.create(
                    NewTransfer(
                        kind = TransferKind.PAYMENT,
                        status = TransferStatus.COMPLETED,
                        fromUserId = callerId,
                        toUserId = counterpartyId,
                        amount = request.amount,
                        note = request.note,
                        groupId = request.groupId?.let { UUID.fromString(it.value) },
                        createdBy = callerId,
                        clientRequestId = request.clientRequestId,
                        completedAt = Clock.System.now(),
                    )
                )
                settle(transfer, callerId, counterpartyId, request.amount, caller.displayName, counterparty.displayName)
                transfer
            }

            TransferKind.REQUEST -> {
                // from = who will pay, to = who is asking.
                val transfer = transfers.create(
                    NewTransfer(
                        kind = TransferKind.REQUEST,
                        status = TransferStatus.PENDING,
                        fromUserId = counterpartyId,
                        toUserId = callerId,
                        amount = request.amount,
                        note = request.note,
                        groupId = request.groupId?.let { UUID.fromString(it.value) },
                        createdBy = callerId,
                        clientRequestId = request.clientRequestId,
                        completedAt = null,
                    )
                )
                notifications.notify(
                    userId = counterpartyId,
                    category = NotificationCategory.PAYMENT,
                    title = "${caller.displayName} requested ${request.amount.toPlainString()}",
                    message = request.note ?: "Open Splitfy to approve or decline the request.",
                )
                transfer
            }
        }
    }

    suspend fun list(userId: UUID, limit: Int): List<Transfer> = transfers.listForUser(userId, limit)

    /** Only the person being asked can approve — approving debits their wallet. */
    suspend fun approve(callerId: UUID, transferId: UUID): Transfer {
        val transfer = load(callerId, transferId)
        if (transfer.kind != TransferKind.REQUEST) {
            throw AppException.Conflict("Only a request can be approved")
        }
        if (transfer.status != TransferStatus.PENDING) {
            throw AppException.Conflict("This request is already ${transfer.status.name.lowercase()}")
        }
        if (transfer.fromUserId.value != callerId.toString()) {
            throw AppException.Forbidden("Only the person being asked can approve this request")
        }

        // Claim the request before moving money, so a decline racing this approval
        // can't leave a DECLINED record whose funds actually moved.
        val completed = transfers.transition(transferId, TransferStatus.PENDING, TransferStatus.COMPLETED, Clock.System.now())
            ?: throw lostRace(transferId)

        val payerId = UUID.fromString(transfer.fromUserId.value)
        val payeeId = UUID.fromString(transfer.toUserId.value)
        try {
            settle(transfer, payerId, payeeId, transfer.amount, transfer.fromName, transfer.toName)
        } catch (e: Exception) {
            // Usually an insufficient balance, which fails the debit before anything moves.
            // Hand the request back; the ledger writes are keyed per transfer, so a later
            // approval won't repeat any leg that did go through.
            transfers.transition(transferId, TransferStatus.COMPLETED, TransferStatus.PENDING, null)
            throw e
        }
        return completed
    }

    suspend fun decline(callerId: UUID, transferId: UUID): Transfer {
        val transfer = load(callerId, transferId)
        if (transfer.status != TransferStatus.PENDING) {
            throw AppException.Conflict("This request is already ${transfer.status.name.lowercase()}")
        }
        val isPayer = transfer.fromUserId.value == callerId.toString()
        val isRequester = transfer.toUserId.value == callerId.toString()
        if (!isPayer && !isRequester) throw AppException.Forbidden("You are not part of this request")

        val status = if (isPayer) TransferStatus.DECLINED else TransferStatus.CANCELLED
        return transfers.transition(transferId, TransferStatus.PENDING, status, null) ?: throw lostRace(transferId)
    }

    /** Another request changed the status between our read and our conditional write. */
    private suspend fun lostRace(transferId: UUID): AppException {
        val current = transfers.findById(transferId) ?: return AppException.NotFound("Transfer")
        return AppException.Conflict("This request is already ${current.status.name.lowercase()}")
    }

    /**
     * Moves the money. The debit runs first so an insufficient balance fails before
     * anything is credited.
     */
    private suspend fun settle(
        transfer: Transfer,
        payerId: UUID,
        payeeId: UUID,
        amount: Money,
        payerName: String,
        payeeName: String,
    ) {
        val transferId = UUID.fromString(transfer.id)

        wallet.applyOrFail(
            LedgerWrite(
                userId = payerId,
                type = LedgerEntryType.TRANSFER_OUT,
                amount = amount,
                description = payeeName,
                counterpartyId = payeeId,
                transferId = transferId,
                note = transfer.note,
                clientRequestId = "transfer-out-${transfer.id}",
            )
        )
        wallet.applyOrFail(
            LedgerWrite(
                userId = payeeId,
                type = LedgerEntryType.TRANSFER_IN,
                amount = amount,
                description = payerName,
                counterpartyId = payerId,
                transferId = transferId,
                note = transfer.note,
                clientRequestId = "transfer-in-${transfer.id}",
            )
        )

        notifications.notify(
            userId = payeeId,
            category = NotificationCategory.PAYMENT,
            title = "$payerName sent you ${amount.toPlainString()}",
            message = transfer.note ?: "The money is in your Splitfy balance.",
        )
    }

    private suspend fun load(callerId: UUID, transferId: UUID): Transfer {
        val transfer = transfers.findById(transferId) ?: throw AppException.NotFound("Transfer")
        val isParty = callerId.toString() in setOf(transfer.fromUserId.value, transfer.toUserId.value)
        if (!isParty) throw AppException.NotFound("Transfer")
        return transfer
    }
}
