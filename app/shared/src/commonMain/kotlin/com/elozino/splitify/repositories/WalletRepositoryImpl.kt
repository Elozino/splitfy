package com.elozino.splitify.repositories

import com.elozino.splitify.api.ApiRoutes
import com.elozino.splitify.api.dto.ActivityFilter
import com.elozino.splitify.api.dto.ActivityResponse
import com.elozino.splitify.api.dto.AddPaymentMethodRequest
import com.elozino.splitify.api.dto.ContactsResponse
import com.elozino.splitify.api.dto.CreateTransferRequest
import com.elozino.splitify.api.dto.NotificationsResponse
import com.elozino.splitify.api.dto.Page
import com.elozino.splitify.api.dto.TopUpRequest
import com.elozino.splitify.api.dto.WalletOperationResponse
import com.elozino.splitify.api.dto.WithdrawRequest
import com.elozino.splitify.DataResult
import com.elozino.splitify.domain.model.LedgerEntry
import com.elozino.splitify.domain.model.LedgerEntryType
import com.elozino.splitify.domain.model.PaymentMethod
import com.elozino.splitify.domain.model.Transfer
import com.elozino.splitify.domain.model.Wallet
import com.elozino.splitify.network.ApiCaller
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class WalletRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : WalletRepository {

    override suspend fun wallet(): DataResult<Wallet> =
        caller.execute { client.get(ApiRoutes.Wallet.BASE) }

    override suspend fun ledger(
        cursor: String?,
        types: List<LedgerEntryType>,
    ): DataResult<Page<LedgerEntry>> = caller.execute {
        client.get(ApiRoutes.Wallet.LEDGER) {
            cursor?.let { parameter("cursor", it) }
            types.forEach { parameter("type", it.name) }
        }
    }

    override suspend fun topUp(request: TopUpRequest): DataResult<WalletOperationResponse> =
        caller.execute { client.post(ApiRoutes.Wallet.TOP_UP) { setBody(request) } }

    override suspend fun withdraw(request: WithdrawRequest): DataResult<WalletOperationResponse> =
        caller.execute { client.post(ApiRoutes.Wallet.WITHDRAW) { setBody(request) } }

    override suspend fun paymentMethods(): DataResult<List<PaymentMethod>> =
        caller.execute { client.get(ApiRoutes.PaymentMethods.BASE) }

    override suspend fun addPaymentMethod(request: AddPaymentMethodRequest): DataResult<PaymentMethod> =
        caller.execute { client.post(ApiRoutes.PaymentMethods.BASE) { setBody(request) } }

    override suspend fun makeDefaultPaymentMethod(id: String): DataResult<List<PaymentMethod>> =
        caller.execute { client.post(ApiRoutes.PaymentMethods.makeDefault(id)) }

    override suspend fun deletePaymentMethod(id: String): DataResult<List<PaymentMethod>> =
        caller.execute { client.delete(ApiRoutes.PaymentMethods.byId(id)) }
}

class TransferRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : TransferRepository {

    override suspend fun list(): DataResult<List<Transfer>> =
        caller.execute { client.get(ApiRoutes.Transfers.BASE) }

    override suspend fun create(request: CreateTransferRequest): DataResult<Transfer> =
        caller.execute { client.post(ApiRoutes.Transfers.BASE) { setBody(request) } }

    override suspend fun approve(id: String): DataResult<Transfer> =
        caller.execute { client.post(ApiRoutes.Transfers.approve(id)) }

    override suspend fun decline(id: String): DataResult<Transfer> =
        caller.execute { client.post(ApiRoutes.Transfers.decline(id)) }
}

class ActivityRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : ActivityRepository {

    override suspend fun activity(filter: ActivityFilter): DataResult<ActivityResponse> =
        caller.execute {
            client.get(ApiRoutes.Activity.BASE) { parameter("filter", filter.name) }
        }
}

class NotificationRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : NotificationRepository {

    override suspend fun list(): DataResult<NotificationsResponse> =
        caller.execute { client.get(ApiRoutes.Notifications.BASE) }

    override suspend fun markRead(id: String): DataResult<NotificationsResponse> =
        caller.execute { client.post(ApiRoutes.Notifications.read(id)) }

    override suspend fun markAllRead(): DataResult<NotificationsResponse> =
        caller.execute { client.post(ApiRoutes.Notifications.READ_ALL) }
}

class ContactRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : ContactRepository {

    override suspend fun contacts(query: String?): DataResult<ContactsResponse> =
        caller.execute {
            client.get(ApiRoutes.Contacts.BASE) {
                query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
            }
        }

    override suspend fun setFavorite(userId: String, favorite: Boolean): DataResult<ContactsResponse> =
        caller.execute {
            client.post(ApiRoutes.Contacts.favorite(userId)) { parameter("favorite", favorite) }
        }
}
