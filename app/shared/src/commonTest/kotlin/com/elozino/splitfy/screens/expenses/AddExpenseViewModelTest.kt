package com.elozino.splitfy.screens.expenses

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
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.api.dto.UploadRules
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.ExpenseId
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.GroupMember
import com.elozino.splitfy.domain.model.GroupRole
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.repositories.ExpenseRepository
import com.elozino.splitfy.repositories.GroupRepository
import com.elozino.splitfy.screens.expenses.add.AddExpenseViewModel
import com.elozino.splitfy.storage.SessionStorage
import com.elozino.splitfy.uploads.FakeUploadRepository
import com.elozino.splitfy.uploads.ImageCompressor
import com.elozino.splitfy.uploads.MarkingCompressor
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AddExpenseViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val epoch = Instant.fromEpochSeconds(0)
    private val me = UserId("00000000-0000-4000-8000-000000000001")
    private val photo = byteArrayOf(1, 2, 3)

    private val group = Group(
        id = GroupId("10000000-0000-4000-8000-000000000001"),
        name = "Flat",
        currency = CurrencyCode.NGN,
        members = listOf(GroupMember(me, "Me", role = GroupRole.OWNER, joinedAt = epoch)),
        createdBy = me,
        createdAt = epoch,
    )

    private val uploads = FakeUploadRepository()
    private val expenses = RecordingExpenses()

    private fun viewModel(compressor: ImageCompressor = MarkingCompressor): AddExpenseViewModel {
        val session = SessionManager(SessionStorage(MapSettings()))
        session.onAuthenticated(
            User(id = me, email = "me@example.com", displayName = "Me", createdAt = epoch),
            TokenPair("access", "refresh", 900),
        )
        return AddExpenseViewModel(group.id.value, OneGroup(group), expenses, session, uploads, compressor, dispatcher)
            .apply {
                onTitleChange("Dinner")
                onAmountChange("50")
            }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the receipt row only shows when the server accepts uploads`() = runTest(dispatcher) {
        uploads.enabled = false
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.uploadsEnabled)

        uploads.enabled = true
        val enabled = viewModel()
        advanceUntilIdle()
        assertTrue(enabled.state.value.uploadsEnabled)
    }

    @Test
    fun `a photo is compressed and uploaded and saved with the expense`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReceiptImage(photo)
        advanceUntilIdle()

        val call = uploads.uploads.single()
        assertEquals(UploadPurpose.RECEIPT, call.purpose)
        assertEquals(UploadRules.JPEG, call.contentType)
        assertContentEquals(photo + 0x7F, call.bytes, "the compressed bytes are what gets uploaded")

        val state = vm.state.value
        assertEquals("groups/g/receipts/key.jpg", state.receiptUrl)
        assertContentEquals(photo + 0x7F, state.receiptPreview)
        assertFalse(state.isUploadingReceipt)

        vm.save("request-1")
        advanceUntilIdle()
        assertEquals("groups/g/receipts/key.jpg", expenses.created.single().receiptUrl)
    }

    @Test
    fun `saving waits for an upload in progress`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>().also { uploads.gate = it }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReceiptImage(photo)
        advanceUntilIdle()
        assertTrue(vm.state.value.isUploadingReceipt)
        assertFalse(vm.state.value.canSave)
        vm.save("request-1")
        advanceUntilIdle()
        assertTrue(expenses.created.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `a failed upload explains why and leaves no receipt attached`() = runTest(dispatcher) {
        uploads.uploadResult = DataResult.Failure(DataError.Offline)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReceiptImage(photo)
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.receiptUrl)
        assertNull(state.receiptPreview)
        assertFalse(state.isUploadingReceipt)
        assertEquals("You're offline. Check your connection and try again.", state.errorMessage)

        vm.save("request-1")
        advanceUntilIdle()
        assertNull(expenses.created.single().receiptUrl)
    }

    @Test
    fun `uploads being switched off on the server is reported`() = runTest(dispatcher) {
        uploads.uploadResult = DataResult.Failure(
            DataError.Api(ApiErrorCode.STORAGE_UNAVAILABLE, "File uploads are not available on this server")
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReceiptImage(photo)
        advanceUntilIdle()

        assertEquals("File uploads are not available on this server", vm.state.value.errorMessage)
        assertNull(vm.state.value.receiptUrl)
    }

    @Test
    fun `an unreadable image is never uploaded`() = runTest(dispatcher) {
        val vm = viewModel(compressor = { null })
        advanceUntilIdle()

        vm.onReceiptImage(photo)
        advanceUntilIdle()

        assertTrue(uploads.uploads.isEmpty())
        assertEquals("That file isn't a photo we can read", vm.state.value.errorMessage)
    }

    @Test
    fun `a removed receipt is not saved`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReceiptImage(photo)
        advanceUntilIdle()
        vm.removeReceipt()

        assertNull(vm.state.value.receiptUrl)
        assertNull(vm.state.value.receiptPreview)
        vm.save("request-1")
        advanceUntilIdle()
        assertNull(expenses.created.single().receiptUrl)
    }

    private class OneGroup(private val group: Group) : GroupRepository {
        override suspend fun get(groupId: String) = DataResult.Success(group)
        override suspend fun list(includeArchived: Boolean): DataResult<List<GroupSummary>> = TODO()
        override suspend fun create(request: CreateGroupRequest): DataResult<Group> = TODO()
        override suspend fun update(groupId: String, request: UpdateGroupRequest): DataResult<Group> = TODO()
        override suspend fun setArchived(groupId: String, archived: Boolean): DataResult<Group> = TODO()
        override suspend fun balances(groupId: String): DataResult<GroupBalancesResponse> = TODO()
        override suspend fun addMemberByEmail(groupId: String, email: String): DataResult<Group> = TODO()
        override suspend fun removeMember(groupId: String, userId: String): DataResult<Group> = TODO()
    }

    private inner class RecordingExpenses : ExpenseRepository {
        val created = mutableListOf<CreateExpenseRequest>()

        override suspend fun create(groupId: String, request: CreateExpenseRequest): DataResult<Expense> {
            created += request
            return DataResult.Success(
                Expense(
                    id = ExpenseId("e1"),
                    groupId = group.id,
                    description = request.description,
                    total = request.total,
                    paidBy = request.paidBy,
                    splitMethod = SplitMethod.EQUAL,
                    shares = emptyList(),
                    receiptUrl = request.receiptUrl,
                    incurredAt = epoch,
                    createdBy = me,
                    createdAt = epoch,
                    updatedAt = epoch,
                )
            )
        }

        override suspend fun list(groupId: String, cursor: String?): DataResult<Page<Expense>> = TODO()
        override suspend fun get(groupId: String, expenseId: String): DataResult<Expense> = TODO()
        override suspend fun delete(groupId: String, expenseId: String): DataResult<Unit> = TODO()
    }
}
