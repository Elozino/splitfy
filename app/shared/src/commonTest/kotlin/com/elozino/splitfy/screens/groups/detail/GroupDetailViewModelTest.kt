package com.elozino.splitfy.screens.groups.detail

import com.elozino.splitfy.DataError
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.api.ApiErrorCode
import com.elozino.splitfy.api.dto.CreateExpenseRequest
import com.elozino.splitfy.api.dto.CreateGroupRequest
import com.elozino.splitfy.api.dto.GroupBalancesResponse
import com.elozino.splitfy.api.dto.GroupSummary
import com.elozino.splitfy.api.dto.Page
import com.elozino.splitfy.api.dto.TokenPair
import com.elozino.splitfy.api.dto.UpdateGroupRequest
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.GroupMember
import com.elozino.splitfy.domain.model.GroupRole
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.repositories.ExpenseRepository
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.screens.groups.detail.shared.GroupDetailViewModel
import com.elozino.splitfy.storage.SessionStorage
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
class GroupDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val epoch = Instant.fromEpochSeconds(0)

    private val owner = UserId("00000000-0000-4000-8000-000000000001")
    private val member = UserId("00000000-0000-4000-8000-000000000002")
    private val newcomer = UserId("00000000-0000-4000-8000-000000000003")

    private val group = Group(
        id = GroupId("10000000-0000-4000-8000-000000000001"),
        name = "Flat",
        currency = CurrencyCode.NGN,
        members = listOf(
            GroupMember(owner, "Owner", role = GroupRole.OWNER, joinedAt = epoch),
            GroupMember(member, "Member", joinedAt = epoch),
        ),
        createdBy = owner,
        createdAt = epoch,
    )

    private val groups = FakeGroups(group)

    private fun viewModel(signedInAs: UserId): GroupDetailViewModel {
        val session = SessionManager(SessionStorage(MapSettings()))
        session.onAuthenticated(
            User(id = signedInAs, email = "x@example.com", displayName = "X", createdAt = epoch),
            TokenPair("access", "refresh", 900),
        )
        return GroupDetailViewModel(group.id.value, groups, NoExpenses, session)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `only owners and admins manage members and never remove the owner or themselves`() = runTest(dispatcher) {
        val asOwner = viewModel(owner)
        val asMember = viewModel(member)
        advanceUntilIdle()

        val state = asOwner.state.value
        assertTrue(state.canManageMembers)
        assertTrue(state.canRemove(group.members[1]))
        assertFalse(state.canRemove(group.members[0]))
        assertFalse(asMember.state.value.canManageMembers)
    }

    @Test
    fun `adding a member by email updates the group and closes the dialog`() = runTest(dispatcher) {
        val vm = viewModel(owner)
        advanceUntilIdle()

        vm.setAddMemberDialogVisible(true)
        vm.onMemberEmailChange(" new@example.com ")
        vm.addMember()
        advanceUntilIdle()

        assertEquals("new@example.com", groups.addedEmails.single())
        assertEquals(3, vm.state.value.group?.members?.size)
        assertFalse(vm.state.value.isAddMemberDialogVisible)
    }

    @Test
    fun `an email without an account says so`() = runTest(dispatcher) {
        groups.addResult = DataResult.Failure(DataError.NotFound("User not found"))
        val vm = viewModel(owner)
        advanceUntilIdle()

        vm.setAddMemberDialogVisible(true)
        vm.onMemberEmailChange("ghost@example.com")
        vm.addMember()
        advanceUntilIdle()

        assertEquals("No one with that email uses splitfy yet", vm.state.value.memberError)
        assertTrue(vm.state.value.isAddMemberDialogVisible)
    }

    @Test
    fun `an invalid email is rejected before calling the server`() = runTest(dispatcher) {
        val vm = viewModel(owner)
        advanceUntilIdle()

        vm.onMemberEmailChange("not-an-email")
        vm.addMember()
        advanceUntilIdle()

        assertTrue(groups.addedEmails.isEmpty())
        assertEquals("Enter a valid email address", vm.state.value.memberError)
    }

    @Test
    fun `removing a member with a balance shows the server's reason`() = runTest(dispatcher) {
        groups.removeResult = DataResult.Failure(
            DataError.Api(ApiErrorCode.CONFLICT, "Settle up before leaving: balance is -50.00")
        )
        val vm = viewModel(owner)
        advanceUntilIdle()

        vm.removeMember(member.value)
        advanceUntilIdle()

        assertEquals("Settle up before leaving: balance is -50.00", vm.state.value.errorMessage)
        assertEquals(2, vm.state.value.group?.members?.size)
    }

    @Test
    fun `removing a settled member updates the list`() = runTest(dispatcher) {
        val vm = viewModel(owner)
        advanceUntilIdle()

        vm.removeMember(member.value)
        advanceUntilIdle()

        assertNull(vm.state.value.errorMessage)
        assertEquals(listOf(owner), vm.state.value.group?.members?.map { it.userId })
    }

    private inner class FakeGroups(private var current: Group) : GroupRepository {
        val addedEmails = mutableListOf<String>()
        var addResult: DataResult<Group>? = null
        var removeResult: DataResult<Group>? = null

        override suspend fun get(groupId: String) = DataResult.Success(current)
        override suspend fun balances(groupId: String) =
            DataResult.Success(GroupBalancesResponse(groupId, current.currency, emptyList(), emptyList()))

        override suspend fun addMemberByEmail(groupId: String, email: String): DataResult<Group> {
            addedEmails += email
            return addResult ?: DataResult.Success(
                current.copy(members = current.members + GroupMember(newcomer, "New", joinedAt = epoch))
                    .also { current = it }
            )
        }

        override suspend fun removeMember(groupId: String, userId: String): DataResult<Group> =
            removeResult ?: DataResult.Success(
                current.copy(members = current.members.filterNot { it.userId.value == userId })
                    .also { current = it }
            )

        override suspend fun list(includeArchived: Boolean): DataResult<List<GroupSummary>> = TODO()
        override suspend fun create(request: CreateGroupRequest): DataResult<Group> = TODO()
        override suspend fun update(groupId: String, request: UpdateGroupRequest): DataResult<Group> = TODO()
        override suspend fun setArchived(groupId: String, archived: Boolean): DataResult<Group> = TODO()
    }

    private object NoExpenses : ExpenseRepository {
        override suspend fun list(groupId: String, cursor: String?) = DataResult.Success(Page<Expense>(emptyList(), null))
        override suspend fun get(groupId: String, expenseId: String): DataResult<Expense> = TODO()
        override suspend fun create(groupId: String, request: CreateExpenseRequest): DataResult<Expense> = TODO()
        override suspend fun delete(groupId: String, expenseId: String): DataResult<Unit> = TODO()
    }
}
