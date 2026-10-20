package com.elozino.splitfy.screens.transactions.shared

import com.elozino.splitfy.TransactionType
import com.elozino.splitfy.Transaction
import com.elozino.splitfy.TransactionUser
import com.elozino.splitfy.PermissionStatus
import com.elozino.splitfy.api.dto.Contact
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.LedgerEntry
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Transfer

enum class ScanStep { SCAN, PROFILE, AMOUNT, CONFIRMATION, SUCCESS }

data class TransactionState(
    val isLoading: Boolean = true,
    val balance: Money = Money.zero(CurrencyCode.NGN),
    val contacts: List<Contact> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(),
    val pendingRequests: List<Transfer> = emptyList(),
    val filter: TransactionType = TransactionType.ALL,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val isSubmitting: Boolean = false,
    val lastTransfer: Transfer? = null,
    val errorMessage: String? = null,
    val selectedUser: TransactionUser? = null,
    val isConfirmationVisible: Boolean = false,
    val isSuccessVisible: Boolean = false,
    val selectedTransaction: Transaction? = null,
    val isReceiptVisible: Boolean = false,
    val isReceiptShareVisible: Boolean = false,
    val scanStep: ScanStep = ScanStep.SCAN,
    val scanAmount: String = "",
    val scanRecipient: Contact? = null,
    val isRecipientPickerVisible: Boolean = false,
    val cameraPermissionStatus: PermissionStatus = PermissionStatus.DENIED,
) {
    val hasMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
}

