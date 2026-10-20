package com.elozino.splitfy.screens.settlements

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.CreateSettlementRequest
import com.elozino.splitfy.api.dto.GroupBalancesResponse
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.api.dto.TokenPair
import com.elozino.splitfy.api.dto.UpdateGroupRequest
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.GroupMember
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SettlementId
import com.elozino.splitfy.domain.model.SettlementStatus
import com.elozino.splitfy.domain.model.SuggestedTransfer
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.repositories.SettlementRepository
import com.elozino.splitfy.storage.SessionStorage
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettleUpViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val epoch = Instant.fromEpochSeconds(0)
    private val ngn = CurrencyCode.NGN

    private val me = UserId("00000000-0000-4000-8000-000000000001")
    private val ada = UserId("00000000-0000-4000-8000-000000000002")
    private val bayo = UserId("00000000-0000-4000-8000-000000000003")
    private val chi = UserId("00000000-0000-4000-8000-000000000004")

    private val group = Group(
        id = GroupId("10000000-0000-4000-8000-000000000001"),
        name = "Lagos trip",
        currency = ngn,
        members = listOf(me, ada, bayo, chi).mapIndexed { i, id ->
            GroupMember(id, listOf("Me", "Ada", "Bayo", "Chi")[i], joinedAt = epoch)
        },
        createdBy = me,
        createdAt = epoch,
    )

    private val groups = FakeGroups(group)
    private val settlements = FakeSettlements(group.id, epoch)

    private fun naira(major: Long) = Money(major * 100, ngn)

    private fun viewModel(): SettleUpViewModel {
        val session = SessionManager(SessionStorage(MapSettings()))
        session.onAuthenticated(
            User(id = me, email = "me@example.com", displayName = "Me", createdAt = epoch),
            TokenPair("access", "refresh", 900),
        )
        return SettleUpViewModel(group.id.value, groups, settlements, session)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `transfers are split into yours and other members'`() = runTest(dispatcher) {
        groups.transfers = listOf(
            SuggestedTransfer(from = me, to = ada, amount = naira(500)),
            SuggestedTransfer(from = bayo, to = me, amount = naira(200)),
            SuggestedTransfer(from = chi, to = ada, amount = naira(100)),
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(ada, me), vm.state.value.yourTransfers.map { it.to })
        assertEquals(listOf(chi), vm.state.value.otherTransfers.map { it.from })
    }

    @Test
    fun `paying records a pending settlement and leaves the rest outstanding`() = runTest(dispatcher) {
        groups.transfers = listOf(SuggestedTransfer(from = me, to = ada, amount = naira(500)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.startPayment(vm.state.value.yourTransfers.single())
        vm.onAmountChange("200")
        vm.submitPayment()
        advanceUntilIdle()

        val created = settlements.stored.single()
        assertEquals(SettlementStatus.PENDING, created.status)
        assertEquals(naira(200), created.amount)
        assertNull(vm.state.value.draft)
        // Balances only count confirmed payments; the pending 200 is taken off locally.
        assertEquals(naira(300), vm.state.value.yourTransfers.single().amount)
        assertEquals(listOf(created), vm.state.value.awaitingTheirConfirmation)
    }

    @Test
    fun `the payee recording a payment confirms it straight away`() = runTest(dispatcher) {
        groups.transfers = listOf(SuggestedTransfer(from = bayo, to = me, amount = naira(200)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.startPayment(vm.state.value.yourTransfers.single())
        vm.submitPayment()
        advanceUntilIdle()

        assertEquals(SettlementStatus.CONFIRMED, settlements.stored.single().status)
    }

    @Test
    fun `a retried submission reuses the same idempotency key`() = runTest(dispatcher) {
        groups.transfers = listOf(SuggestedTransfer(from = me, to = ada, amount = naira(500)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.startPayment(vm.state.value.yourTransfers.single())

        settlements.failNextCreate = true
        vm.submitPayment()
        advanceUntilIdle()
        vm.submitPayment()
        advanceUntilIdle()

        assertEquals(2, settlements.requests.size)
        assertEquals(1, settlements.requests.map { it.clientRequestId }.distinct().size)
    }

    @Test
    fun `an invalid amount is rejected without calling the server`() = runTest(dispatcher) {
        groups.transfers = listOf(SuggestedTransfer(from = me, to = ada, amount = naira(500)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.startPayment(vm.state.value.yourTransfers.single())
        vm.onAmountChange("0")
        vm.submitPayment()
        advanceUntilIdle()

        assertTrue(settlements.requests.isEmpty())
        assertEquals("Enter an amount greater than zero", vm.state.value.errorMessage)
    }

    @Test
    fun `confirming a payment sent to you moves it into history`() = runTest(dispatcher) {
        settlements.stored += settlements.settlement(from = ada, to = me, amount = naira(150))
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirm(vm.state.value.awaitingYourConfirmation.single())
        advanceUntilIdle()

        assertTrue(vm.state.value.awaitingYourConfirmation.isEmpty())
        assertEquals(naira(150), vm.state.value.history.single().amount)
    }
}

private class FakeGroups(private val group: Group) : GroupRepository {
    var transfers: List<SuggestedTransfer> = emptyList()

    override suspend fun get(groupId: String) = DataResult.Success(group)
    override suspend fun balances(groupId: String) =
        DataResult.Success(GroupBalancesResponse(group.id.value, group.currency, emptyList(), transfers))

    override suspend fun list(includeArchived: Boolean): DataResult<List<GroupSummary>> = TODO()
    override suspend fun create(request: CreateGroupRequest): DataResult<Group> = TODO()
    override suspend fun update(groupId: String, request: UpdateGroupRequest): DataResult<Group> = TODO()
    override suspend fun setArchived(groupId: String, archived: Boolean): DataResult<Group> = TODO()
    override suspend fun addMemberByEmail(groupId: String, email: String): DataResult<Group> = TODO()
    override suspend fun removeMember(groupId: String, userId: String): DataResult<Group> = TODO()
}

/** Mirrors the server's settlement rules closely enough to drive the view model. */
private class FakeSettlements(private val groupId: GroupId, private val now: Instant) : SettlementRepository {
    val stored = mutableListOf<Settlement>()
    val requests = mutableListOf<CreateSettlementRequest>()
    var failNextCreate = false

    fun settlement(from: UserId, to: UserId, amount: Money) = Settlement(
        id = SettlementId("20000000-0000-4000-8000-00000000000${stored.size}"),
        groupId = groupId,
        fromUserId = from,
        toUserId = to,
        amount = amount,
        settledAt = now,
        createdBy = from,
        createdAt = now,
    )

    override suspend fun list(groupId: String) = DataResult.Success(stored.toList())

    override suspend fun create(groupId: String, request: CreateSettlementRequest): DataResult<Settlement> {
        requests += request
        if (failNextCreate) {
            failNextCreate = false
            return DataResult.Failure(DataError.Timeout)
        }
        return DataResult.Success(
            settlement(request.fromUserId, request.toUserId, request.amount).also { stored += it }
        )
    }

    override suspend fun confirm(groupId: String, settlementId: String) = update(settlementId, SettlementStatus.CONFIRMED)

    override suspend fun cancel(groupId: String, settlementId: String) = update(settlementId, SettlementStatus.CANCELLED)

    private fun update(id: String, status: SettlementStatus): DataResult<Settlement> {
        val index = stored.indexOfFirst { it.id.value == id }
        stored[index] = stored[index].copy(status = status)
        return DataResult.Success(stored[index])
    }
}
