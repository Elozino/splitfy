package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.backend.schema.dbNow
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.ExpenseSharesTable
import com.elozino.splitfy.backend.schema.ExpensesTable
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.schema.toKotlin
import com.elozino.splitfy.backend.utils.Cursor
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.ExpenseCategory
import com.elozino.splitfy.domain.model.ExpenseId
import com.elozino.splitfy.domain.model.ExpenseShare
import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SplitMethod
import com.elozino.splitfy.domain.model.UserId
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update

/** Everything needed to persist an expense once the split has been computed. */
data class NewExpense(
    val groupId: UUID,
    val description: String,
    val total: Money,
    val category: ExpenseCategory,
    val paidBy: UUID,
    val splitMethod: SplitMethod,
    val shares: List<ExpenseShare>,
    val receiptUrl: String?,
    val notes: String?,
    val incurredAt: Instant,
    val createdBy: UUID,
    val clientRequestId: String?,
)

class ExpenseRepository {

    suspend fun create(new: NewExpense): Expense = dbQuery {
        val now = dbNow()
        val expenseId = UUID.randomUUID()

        ExpensesTable.insert {
            it[id] = expenseId
            it[groupId] = new.groupId
            it[description] = new.description.trim()
            it[amount] = new.total.amount
            it[currency] = new.total.currency.code
            it[category] = new.category
            it[paidBy] = new.paidBy
            it[splitMethod] = new.splitMethod
            it[receiptUrl] = new.receiptUrl
            it[notes] = new.notes
            it[incurredAt] = new.incurredAt.toJava()
            it[createdBy] = new.createdBy
            it[createdAt] = now.toJava()
            it[updatedAt] = now.toJava()
            it[clientRequestId] = new.clientRequestId
        }
        insertShares(expenseId, new.shares)

        loadExpense(expenseId) ?: error("Expense $expenseId vanished immediately after insert")
    }

    suspend fun findById(id: UUID): Expense? = dbQuery { loadExpense(id) }

    suspend fun findByClientRequestId(groupId: UUID, clientRequestId: String): Expense? = dbQuery {
        ExpensesTable.selectAll()
            .where {
                (ExpensesTable.groupId eq groupId) and
                    (ExpensesTable.clientRequestId eq clientRequestId)
            }
            .singleOrNull()
            ?.let { loadExpense(it[ExpensesTable.id]) }
    }

    /** Returns up to `limit + 1` rows so the caller can detect a next page. */
    suspend fun listForGroup(groupId: UUID, cursor: Cursor?, limit: Int): List<Expense> = dbQuery {
        val rows = ExpensesTable.selectAll()
            .where {
                var condition = (ExpensesTable.groupId eq groupId) and ExpensesTable.deletedAt.isNull()
                if (cursor != null) {
                    condition = condition and (
                        (ExpensesTable.createdAt less cursor.createdAt.toJava()) or
                            ((ExpensesTable.createdAt eq cursor.createdAt.toJava()) and (ExpensesTable.id less cursor.id))
                        )
                }
                condition
            }
            .orderBy(ExpensesTable.createdAt to SortOrder.DESC, ExpensesTable.id to SortOrder.DESC)
            .limit(limit + 1)
            .toList()

        val sharesByExpense = loadShares(rows.map { it[ExpensesTable.id] })
        rows.map { it.toExpense(sharesByExpense[it[ExpensesTable.id]].orEmpty()) }
    }

    /** The full, un-paginated ledger a balance calculation needs. */
    suspend fun allForGroup(groupId: UUID): List<Expense> = dbQuery {
        val rows = ExpensesTable.selectAll()
            .where { (ExpensesTable.groupId eq groupId) and ExpensesTable.deletedAt.isNull() }
            .toList()
        val sharesByExpense = loadShares(rows.map { it[ExpensesTable.id] })
        rows.map { it.toExpense(sharesByExpense[it[ExpensesTable.id]].orEmpty()) }
    }

    suspend fun update(
        id: UUID,
        description: String?,
        total: Money?,
        category: ExpenseCategory?,
        paidBy: UUID?,
        splitMethod: SplitMethod?,
        shares: List<ExpenseShare>?,
        receiptUrl: String?,
        notes: String?,
        incurredAt: Instant?,
    ): Expense? = dbQuery {
        ExpensesTable.update({ ExpensesTable.id eq id }) { statement ->
            description?.let { statement[ExpensesTable.description] = it.trim() }
            total?.let {
                statement[amount] = it.amount
                statement[currency] = it.currency.code
            }
            category?.let { statement[ExpensesTable.category] = it }
            paidBy?.let { statement[ExpensesTable.paidBy] = it }
            splitMethod?.let { statement[ExpensesTable.splitMethod] = it }
            receiptUrl?.let { statement[ExpensesTable.receiptUrl] = it.ifEmpty { null } }
            notes?.let { statement[ExpensesTable.notes] = it }
            incurredAt?.let { statement[ExpensesTable.incurredAt] = it.toJava() }
            statement[updatedAt] = Clock.System.now().toJava()
        }
        if (shares != null) {
            ExpenseSharesTable.deleteWhere { expenseId eq id }
            insertShares(id, shares)
        }
        loadExpense(id)
    }

    /**
     * Soft delete: balances are a fold over history, and hard-deleting a row would
     * silently rewrite every past statement a member has already seen.
     */
    suspend fun softDelete(id: UUID): Boolean = dbQuery {
        ExpensesTable.update({ (ExpensesTable.id eq id) and ExpensesTable.deletedAt.isNull() }) {
            val now = dbNow()
            it[deletedAt] = now.toJava()
            it[updatedAt] = now.toJava()
        } > 0
    }

    // --- helpers, always called inside a transaction ------------------------

    private fun insertShares(expenseId: UUID, shares: List<ExpenseShare>) {
        ExpenseSharesTable.batchInsert(shares) { share ->
            this[ExpenseSharesTable.id] = UUID.randomUUID()
            this[ExpenseSharesTable.expenseId] = expenseId
            this[ExpenseSharesTable.userId] = UUID.fromString(share.userId.value)
            this[ExpenseSharesTable.amount] = share.amount.amount
            this[ExpenseSharesTable.weight] = share.weight
        }
    }

    private fun loadExpense(id: UUID): Expense? {
        val row = ExpensesTable.selectAll().where { ExpensesTable.id eq id }.singleOrNull() ?: return null
        return row.toExpense(loadShares(listOf(id))[id].orEmpty())
    }

    private fun loadShares(expenseIds: List<UUID>): Map<UUID, List<ExpenseShare>> {
        if (expenseIds.isEmpty()) return emptyMap()
        val currencyByExpense = ExpensesTable
            .selectAll()
            .where { ExpensesTable.id inList expenseIds }
            .associate { it[ExpensesTable.id] to CurrencyCode(it[ExpensesTable.currency]) }

        return ExpenseSharesTable.selectAll()
            .where { ExpenseSharesTable.expenseId inList expenseIds }
            .groupBy({ it[ExpenseSharesTable.expenseId] }) { row ->
                val expenseId = row[ExpenseSharesTable.expenseId]
                ExpenseShare(
                    userId = UserId(row[ExpenseSharesTable.userId].toString()),
                    amount = Money(
                        row[ExpenseSharesTable.amount],
                        currencyByExpense[expenseId] ?: CurrencyCode.NGN,
                    ),
                    weight = row[ExpenseSharesTable.weight],
                )
            }
    }
}

private fun ResultRow.toExpense(shares: List<ExpenseShare>) = Expense(
    id = ExpenseId(this[ExpensesTable.id].toString()),
    groupId = GroupId(this[ExpensesTable.groupId].toString()),
    description = this[ExpensesTable.description],
    total = Money(this[ExpensesTable.amount], CurrencyCode(this[ExpensesTable.currency])),
    category = this[ExpensesTable.category],
    paidBy = UserId(this[ExpensesTable.paidBy].toString()),
    splitMethod = this[ExpensesTable.splitMethod],
    shares = shares,
    receiptUrl = this[ExpensesTable.receiptUrl],
    notes = this[ExpensesTable.notes],
    incurredAt = this[ExpensesTable.incurredAt].toKotlin(),
    createdBy = UserId(this[ExpensesTable.createdBy].toString()),
    createdAt = this[ExpensesTable.createdAt].toKotlin(),
    updatedAt = this[ExpensesTable.updatedAt].toKotlin(),
    deletedAt = this[ExpensesTable.deletedAt]?.toKotlin(),
)
