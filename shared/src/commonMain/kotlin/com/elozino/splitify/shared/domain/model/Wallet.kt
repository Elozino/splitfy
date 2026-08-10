package com.elozino.splitify.shared.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** A user's spendable balance, held per currency. */
@Serializable
data class Wallet(
    val userId: UserId,
    val balance: Money,
    val updatedAt: Instant,
)

@Serializable
enum class LedgerEntryType {
    TOP_UP,
    WITHDRAWAL,
    TRANSFER_OUT,
    TRANSFER_IN,
    EXPENSE_SETTLEMENT;

    /** Whether the entry increases the balance. Drives the +/- sign in the UI. */
    val isCredit: Boolean
        get() = this == TOP_UP || this == TRANSFER_IN
}

/**
 * One immutable row of the money ledger.
 *
 * `balanceAfter` is stored rather than recomputed so a statement always shows the
 * balance as it stood at that moment, even after later entries are added.
 */
@Serializable
data class LedgerEntry(
    val id: String,
    val type: LedgerEntryType,
    val amount: Money,
    val balanceAfter: Money,
    val description: String,
    val counterpartyName: String? = null,
    val paymentMethodLabel: String? = null,
    val paymentMethodBrand: String? = null,
    val note: String? = null,
    val occurredAt: Instant,
) {
    /** Signed amount for display: credits positive, debits negative. */
    val signedAmount: Money get() = if (type.isCredit) amount else -amount
}

@Serializable
enum class PaymentMethodType { CARD, BANK_ACCOUNT, PAYPAL, GOOGLE_PAY, APPLE_PAY }

/**
 * A saved funding source.
 *
 * Only the provider's opaque token and the display digits are ever stored — the app
 * never sees or transmits a full card number.
 */
@Serializable
data class PaymentMethod(
    val id: String,
    val type: PaymentMethodType,
    val label: String,
    val brand: String? = null,
    val last4: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Instant,
) {
    /** `**** **** **** 4679` for cards, the plain label otherwise. */
    val maskedLabel: String
        get() = if (type == PaymentMethodType.CARD && last4 != null) "**** **** **** $last4" else label
}

@Serializable
enum class TransferKind {
    /** Money moves immediately from the caller to the counterparty. */
    PAYMENT,

    /** The caller asks the counterparty for money; it moves once they approve. */
    REQUEST,
}

@Serializable
enum class TransferStatus { PENDING, COMPLETED, DECLINED, CANCELLED }

@Serializable
data class Transfer(
    val id: String,
    val kind: TransferKind,
    val status: TransferStatus,
    val fromUserId: UserId,
    val fromName: String,
    val toUserId: UserId,
    val toName: String,
    val amount: Money,
    val note: String? = null,
    val groupId: GroupId? = null,
    val createdAt: Instant,
    val completedAt: Instant? = null,
)
