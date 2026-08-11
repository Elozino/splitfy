package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.AddPaymentMethodRequest
import com.elozino.splitfy.api.dto.NotificationCategory
import com.elozino.splitfy.api.dto.TopUpRequest
import com.elozino.splitfy.api.dto.WalletOperationResponse
import com.elozino.splitfy.api.dto.WithdrawRequest
import com.elozino.splitfy.api.FieldError
import com.elozino.splitfy.backend.repositories.InsufficientFundsException
import com.elozino.splitfy.backend.repositories.LedgerWrite
import com.elozino.splitfy.backend.repositories.PaymentMethodRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.repositories.WalletRepository
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.backend.utils.Cursor
import com.elozino.splitfy.domain.model.LedgerEntry
import com.elozino.splitfy.domain.model.LedgerEntryType
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.PaymentMethod
import com.elozino.splitfy.domain.model.Wallet
import java.util.UUID

class WalletService(
    private val wallets: WalletRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val users: UserRepository,
    private val notifications: NotificationService,
) {

    suspend fun wallet(userId: UUID): Wallet {
        val user = users.findById(userId) ?: throw AppException.NotFound("User")
        return wallets.getOrCreate(userId, user.defaultCurrency)
    }

    suspend fun ledger(
        userId: UUID,
        cursor: Cursor?,
        limit: Int,
        types: List<LedgerEntryType>,
    ): List<LedgerEntry> = wallets.ledger(userId, cursor, limit, types)

    suspend fun topUp(userId: UUID, request: TopUpRequest): WalletOperationResponse {
        requirePositive(request.amount)
        val method = requireOwnedMethod(userId, request.paymentMethodId)

        val (wallet, entry) = applyOrFail(
            LedgerWrite(
                userId = userId,
                type = LedgerEntryType.TOP_UP,
                amount = request.amount,
                description = "Top Up",
                paymentMethodId = UUID.fromString(method.id),
                note = "Top up from ${method.maskedLabel}",
                clientRequestId = request.clientRequestId,
            )
        )

        notifications.notify(
            userId = userId,
            category = NotificationCategory.PAYMENT,
            title = "Top up successful",
            message = "${request.amount.toPlainString()} ${request.amount.currency.code} was added to your balance.",
        )
        return WalletOperationResponse(wallet, entry.id)
    }

    suspend fun withdraw(userId: UUID, request: WithdrawRequest): WalletOperationResponse {
        requirePositive(request.amount)
        val method = requireOwnedMethod(userId, request.paymentMethodId)

        val (wallet, entry) = applyOrFail(
            LedgerWrite(
                userId = userId,
                type = LedgerEntryType.WITHDRAWAL,
                amount = request.amount,
                description = "Withdraw",
                paymentMethodId = UUID.fromString(method.id),
                note = "Withdrawal to ${method.maskedLabel}",
                clientRequestId = request.clientRequestId,
            )
        )

        notifications.notify(
            userId = userId,
            category = NotificationCategory.PAYMENT,
            title = "Withdrawal on its way",
            message = "${request.amount.toPlainString()} ${request.amount.currency.code} is heading to ${method.maskedLabel}.",
        )
        return WalletOperationResponse(wallet, entry.id)
    }

    suspend fun paymentMethods(userId: UUID): List<PaymentMethod> = paymentMethods.list(userId)

    suspend fun addPaymentMethod(userId: UUID, request: AddPaymentMethodRequest): PaymentMethod {
        val errors = buildList {
            if (request.label.isBlank()) add(FieldError("label", "Give this method a name"))
            if (request.providerToken.isBlank()) {
                add(FieldError("providerToken", "A provider token is required"))
            }
            request.last4?.let { digits ->
                if (digits.length != 4 || !digits.all(Char::isDigit)) {
                    add(FieldError("last4", "Last 4 must be exactly four digits"))
                }
            }
            // Defence in depth: a full PAN in this field means the client is not
            // tokenising, and storing it would drag the whole backend into PCI scope.
            if (request.providerToken.length >= 12 && request.providerToken.all(Char::isDigit)) {
                add(FieldError("providerToken", "Send a provider token, not a card number"))
            }
        }
        if (errors.isNotEmpty()) throw AppException.Validation(errors)

        return paymentMethods.add(userId, request)
    }

    suspend fun makeDefaultPaymentMethod(userId: UUID, id: UUID): List<PaymentMethod> {
        if (!paymentMethods.makeDefault(userId, id)) throw AppException.NotFound("Payment method")
        return paymentMethods.list(userId)
    }

    suspend fun deletePaymentMethod(userId: UUID, id: UUID): List<PaymentMethod> {
        if (!paymentMethods.delete(userId, id)) throw AppException.NotFound("Payment method")
        return paymentMethods.list(userId)
    }

    /** Shared by the transfer service so both paths report shortfalls identically. */
    internal suspend fun applyOrFail(write: LedgerWrite) = try {
        wallets.apply(write)
    } catch (shortfall: InsufficientFundsException) {
        throw AppException.BadRequest(
            "Your balance of ${shortfall.available.toPlainString()} is not enough",
            ApiErrorCode.INSUFFICIENT_FUNDS,
        )
    }

    private suspend fun requireOwnedMethod(userId: UUID, id: String): PaymentMethod {
        val uuid = runCatching { UUID.fromString(id) }.getOrElse {
            throw AppException.BadRequest("Invalid payment method", ApiErrorCode.PAYMENT_METHOD_REQUIRED)
        }
        return paymentMethods.find(userId, uuid) ?: throw AppException.NotFound("Payment method")
    }

    private fun requirePositive(amount: Money) {
        if (!amount.isPositive) {
            throw AppException.Validation(listOf(FieldError("amount", "Enter an amount greater than zero")))
        }
    }
}
