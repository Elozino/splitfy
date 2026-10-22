package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.SettlementsTable
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.schema.toKotlin
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SettlementId
import com.elozino.splitfy.domain.model.SettlementStatus
import com.elozino.splitfy.domain.model.UserId
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.update

data class NewSettlement(
    val groupId: UUID,
    val fromUserId: UUID,
    val toUserId: UUID,
    val amount: Money,
    val note: String?,
    val settledAt: Instant,
    val createdBy: UUID,
    val clientRequestId: String?,
)

class SettlementRepository {

    suspend fun create(new: NewSettlement): Settlement = dbQuery {
        val id = UUID.randomUUID()
        SettlementsTable.insert {
            it[SettlementsTable.id] = id
            it[groupId] = new.groupId
            it[fromUserId] = new.fromUserId
            it[toUserId] = new.toUserId
            it[amount] = new.amount.amount
            it[currency] = new.amount.currency.code
            it[status] = SettlementStatus.PENDING
            it[note] = new.note
            it[settledAt] = new.settledAt.toJava()
            it[createdBy] = new.createdBy
            it[createdAt] = Clock.System.now().toJava()
            it[clientRequestId] = new.clientRequestId
        }
        loadSettlement(id) ?: error("Settlement $id vanished immediately after insert")
    }

    suspend fun findById(id: UUID): Settlement? = dbQuery { loadSettlement(id) }

    suspend fun findByClientRequestId(groupId: UUID, clientRequestId: String): Settlement? = dbQuery {
        SettlementsTable.selectAll()
            .where {
                (SettlementsTable.groupId eq groupId) and
                    (SettlementsTable.clientRequestId eq clientRequestId)
            }
            .singleOrNull()
            ?.toSettlement()
    }

    suspend fun listForGroup(groupId: UUID): List<Settlement> = dbQuery {
        SettlementsTable.selectAll()
            .where { SettlementsTable.groupId eq groupId }
            .orderBy(SettlementsTable.settledAt, SortOrder.DESC)
            .map { it.toSettlement() }
    }

    /**
     * Moves a settlement from [from] to [to] in a single conditional UPDATE, so the
     * status check and the write can't be split by a concurrent request. Returns null
     * when the row was no longer in [from]; the caller decides what that means.
     */
    suspend fun transition(id: UUID, from: SettlementStatus, to: SettlementStatus): Settlement? = dbQuery {
        val updated = SettlementsTable.update({ (SettlementsTable.id eq id) and (SettlementsTable.status eq from) }) {
            it[status] = to
        }
        if (updated == 0) null else loadSettlement(id)
    }

    private fun loadSettlement(id: UUID): Settlement? =
        SettlementsTable.selectAll().where { SettlementsTable.id eq id }.singleOrNull()?.toSettlement()
}

private fun ResultRow.toSettlement() = Settlement(
    id = SettlementId(this[SettlementsTable.id].toString()),
    groupId = GroupId(this[SettlementsTable.groupId].toString()),
    fromUserId = UserId(this[SettlementsTable.fromUserId].toString()),
    toUserId = UserId(this[SettlementsTable.toUserId].toString()),
    amount = Money(this[SettlementsTable.amount], CurrencyCode(this[SettlementsTable.currency])),
    status = this[SettlementsTable.status],
    note = this[SettlementsTable.note],
    settledAt = this[SettlementsTable.settledAt].toKotlin(),
    createdBy = UserId(this[SettlementsTable.createdBy].toString()),
    createdAt = this[SettlementsTable.createdAt].toKotlin(),
)
