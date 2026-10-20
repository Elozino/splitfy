package com.elozino.splitfy.repositories

import com.elozino.splitfy.api.dto.ActivityFilter
import com.elozino.splitfy.api.dto.ActivityResponse
import com.elozino.splitfy.api.dto.AddPaymentMethodRequest
import com.elozino.splitfy.api.dto.ContactsResponse
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.api.dto.CreateTransferRequest
import com.elozino.splitfy.api.dto.GroupBalancesResponse
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.api.dto.NotificationsResponse
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.api.dto.OAuthSignInRequest
import com.elozino.splitfy.api.dto.Page
import com.elozino.splitfy.api.dto.TopUpRequest
import com.elozino.splitfy.api.dto.UpdateGroupRequest
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.api.dto.WalletOperationResponse
import com.elozino.splitfy.api.dto.WithdrawRequest
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.LedgerEntry
import com.elozino.splitfy.domain.model.LedgerEntryType
import com.elozino.splitfy.domain.model.PaymentMethod
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.Transfer
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.Wallet

/**
 * Repository contracts live in the domain layer and the implementations in `data`,
 * so a ViewModel depends on the interface only — which is what makes them testable
 * without a network stack.
 */
interface AuthRepository {
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        phoneNumber: String? = null,
    ): DataResult<User>

    suspend fun login(email: String, password: String): DataResult<User>

    suspend fun requestPasswordReset(email: String): DataResult<Unit>

    suspend fun verifyOtp(email: String, code: String): DataResult<String>

    suspend fun resetPassword(email: String, resetToken: String, newPassword: String): DataResult<Unit>

    /** Confirms the signed-in user's email with the code sent at sign-up. */
    suspend fun verifyEmail(code: String): DataResult<User>

    suspend fun resendVerification(): DataResult<Unit>

    suspend fun signInWithProvider(provider: OAuthProvider, request: OAuthSignInRequest): DataResult<User>

    suspend fun logout(): DataResult<Unit>

    /**
     * Deletes the signed-in account and, once the server confirms, clears the local
     * session. The server refuses while any group balance is unsettled and names
     * the groups in its message, which is passed through unchanged.
     */
    suspend fun deleteAccount(): DataResult<Unit>
}

interface UserRepository {
    suspend fun me(): DataResult<User>
    suspend fun updateProfile(request: UpdateProfileRequest): DataResult<User>
    suspend fun search(query: String): DataResult<List<User>>
}

interface GroupRepository {
    suspend fun list(includeArchived: Boolean = false): DataResult<List<GroupSummary>>
    suspend fun get(groupId: String): DataResult<Group>
    suspend fun create(request: CreateGroupRequest): DataResult<Group>
    suspend fun update(groupId: String, request: UpdateGroupRequest): DataResult<Group>
    suspend fun setArchived(groupId: String, archived: Boolean): DataResult<Group>
    suspend fun balances(groupId: String): DataResult<GroupBalancesResponse>
    suspend fun addMemberByEmail(groupId: String, email: String): DataResult<Group>
    suspend fun removeMember(groupId: String, userId: String): DataResult<Group>
}

interface ExpenseRepository {
    suspend fun list(groupId: String, cursor: String? = null): DataResult<Page<Expense>>
    suspend fun get(groupId: String, expenseId: String): DataResult<Expense>
    suspend fun create(groupId: String, request: CreateExpenseRequest): DataResult<Expense>
    suspend fun delete(groupId: String, expenseId: String): DataResult<Unit>
}

interface SettlementRepository {
    suspend fun list(groupId: String): DataResult<List<Settlement>>
    suspend fun create(groupId: String, request: CreateSettlementRequest): DataResult<Settlement>
    suspend fun confirm(groupId: String, settlementId: String): DataResult<Settlement>
    suspend fun cancel(groupId: String, settlementId: String): DataResult<Settlement>
}

interface WalletRepository {
    suspend fun wallet(): DataResult<Wallet>
    suspend fun ledger(cursor: String? = null, types: List<LedgerEntryType> = emptyList()): DataResult<Page<LedgerEntry>>
    suspend fun topUp(request: TopUpRequest): DataResult<WalletOperationResponse>
    suspend fun withdraw(request: WithdrawRequest): DataResult<WalletOperationResponse>
    suspend fun paymentMethods(): DataResult<List<PaymentMethod>>
    suspend fun addPaymentMethod(request: AddPaymentMethodRequest): DataResult<PaymentMethod>
    suspend fun makeDefaultPaymentMethod(id: String): DataResult<List<PaymentMethod>>
    suspend fun deletePaymentMethod(id: String): DataResult<List<PaymentMethod>>
}

interface TransferRepository {
    suspend fun list(): DataResult<List<Transfer>>
    suspend fun create(request: CreateTransferRequest): DataResult<Transfer>
    suspend fun approve(id: String): DataResult<Transfer>
    suspend fun decline(id: String): DataResult<Transfer>
}

interface ActivityRepository {
    suspend fun activity(filter: ActivityFilter): DataResult<ActivityResponse>
}

interface NotificationRepository {
    suspend fun list(): DataResult<NotificationsResponse>
    suspend fun markRead(id: String): DataResult<NotificationsResponse>
    suspend fun markAllRead(): DataResult<NotificationsResponse>
}

interface ContactRepository {
    suspend fun contacts(query: String? = null): DataResult<ContactsResponse>
    suspend fun setFavorite(userId: String, favorite: Boolean): DataResult<ContactsResponse>
}
