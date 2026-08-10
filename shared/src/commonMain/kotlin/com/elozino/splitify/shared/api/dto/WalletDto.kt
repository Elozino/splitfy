package com.elozino.splitify.shared.api.dto

import com.elozino.splitify.shared.domain.model.GroupId
import com.elozino.splitify.shared.domain.model.LedgerEntryType
import com.elozino.splitify.shared.domain.model.Money
import com.elozino.splitify.shared.domain.model.PaymentMethodType
import com.elozino.splitify.shared.domain.model.TransferKind
import com.elozino.splitify.shared.domain.model.UserId
import com.elozino.splitify.shared.domain.model.Wallet
import kotlinx.serialization.Serializable

@Serializable
data class TopUpRequest(
    val amount: Money,
    val paymentMethodId: String,
    /** Idempotency key: a retried top-up must not double-credit the wallet. */
    val clientRequestId: String? = null,
)

@Serializable
data class WithdrawRequest(
    val amount: Money,
    val paymentMethodId: String,
    val clientRequestId: String? = null,
)

/** The wallet plus the entry that was just written, so the UI can show a receipt. */
@Serializable
data class WalletOperationResponse(
    val wallet: Wallet,
    val entryId: String,
)

@Serializable
data class LedgerFilter(
    val types: List<LedgerEntryType> = emptyList(),
)

/**
 * Adding a funding source.
 *
 * [providerToken] is an opaque handle issued by the payment provider's SDK on the
 * device. Raw card numbers must never reach this API.
 */
@Serializable
data class AddPaymentMethodRequest(
    val type: PaymentMethodType,
    val label: String,
    val providerToken: String,
    val brand: String? = null,
    val last4: String? = null,
    val makeDefault: Boolean = false,
)

@Serializable
data class CreateTransferRequest(
    val kind: TransferKind,
    val counterpartyId: UserId,
    val amount: Money,
    val note: String? = null,
    val groupId: GroupId? = null,
    val clientRequestId: String? = null,
)
