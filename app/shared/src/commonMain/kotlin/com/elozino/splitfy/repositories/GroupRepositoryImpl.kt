package com.elozino.splitfy.repositories

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.AddMemberRequest
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.api.dto.GroupBalancesResponse
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.api.dto.MessageResponse
import com.elozino.splitfy.api.dto.Page
import com.elozino.splitfy.api.dto.UpdateGroupRequest
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.map
import com.elozino.splitfy.network.ApiCaller
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class UserRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : UserRepository {

    override suspend fun me(): DataResult<User> =
        caller.execute { client.get(ApiRoutes.Users.ME) }

    override suspend fun updateProfile(request: UpdateProfileRequest): DataResult<User> =
        caller.execute { client.patch(ApiRoutes.Users.ME) { setBody(request) } }

    override suspend fun search(query: String): DataResult<List<User>> =
        caller.execute { client.get(ApiRoutes.Users.SEARCH) { parameter("q", query) } }
}

class GroupRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : GroupRepository {

    override suspend fun list(includeArchived: Boolean): DataResult<List<GroupSummary>> =
        caller.execute {
            client.get(ApiRoutes.Groups.BASE) { parameter("includeArchived", includeArchived) }
        }

    override suspend fun get(groupId: String): DataResult<Group> =
        caller.execute { client.get(ApiRoutes.Groups.byId(groupId)) }

    override suspend fun create(request: CreateGroupRequest): DataResult<Group> =
        caller.execute { client.post(ApiRoutes.Groups.BASE) { setBody(request) } }

    override suspend fun update(groupId: String, request: UpdateGroupRequest): DataResult<Group> =
        caller.execute { client.patch(ApiRoutes.Groups.byId(groupId)) { setBody(request) } }

    /**
     * Archiving rather than deleting: balances are history, and a hard delete would
     * silently rewrite statements members have already seen.
     */
    override suspend fun setArchived(groupId: String, archived: Boolean): DataResult<Group> =
        caller.execute {
            client.post(ApiRoutes.Groups.archive(groupId)) { parameter("archived", archived) }
        }

    override suspend fun balances(groupId: String): DataResult<GroupBalancesResponse> =
        caller.execute { client.get(ApiRoutes.Groups.balances(groupId)) }

    override suspend fun addMemberByEmail(groupId: String, email: String): DataResult<Group> =
        caller.execute {
            client.post(ApiRoutes.Groups.members(groupId)) { setBody(AddMemberRequest(email = email)) }
        }

    override suspend fun removeMember(groupId: String, userId: String): DataResult<Group> =
        caller.execute { client.delete(ApiRoutes.Groups.member(groupId, userId)) }
}

class ExpenseRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : ExpenseRepository {

    override suspend fun list(groupId: String, cursor: String?): DataResult<Page<Expense>> =
        caller.execute {
            client.get(ApiRoutes.Expenses.forGroup(groupId)) {
                cursor?.let { parameter("cursor", it) }
            }
        }

    override suspend fun get(groupId: String, expenseId: String): DataResult<Expense> =
        caller.execute { client.get(ApiRoutes.Expenses.byId(groupId, expenseId)) }

    override suspend fun create(groupId: String, request: CreateExpenseRequest): DataResult<Expense> =
        caller.execute {
            client.post(ApiRoutes.Expenses.forGroup(groupId)) { setBody(request) }
        }

    override suspend fun delete(groupId: String, expenseId: String): DataResult<Unit> =
        caller.execute<MessageResponse> {
            client.delete(ApiRoutes.Expenses.byId(groupId, expenseId))
        }.map { }
}

class SettlementRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : SettlementRepository {

    override suspend fun list(groupId: String): DataResult<List<Settlement>> =
        caller.execute { client.get(ApiRoutes.Settlements.forGroup(groupId)) }

    override suspend fun create(
        groupId: String,
        request: CreateSettlementRequest,
    ): DataResult<Settlement> = caller.execute {
        client.post(ApiRoutes.Settlements.forGroup(groupId)) { setBody(request) }
    }

    override suspend fun confirm(groupId: String, settlementId: String): DataResult<Settlement> =
        caller.execute { client.post(ApiRoutes.Settlements.confirm(groupId, settlementId)) }
}
