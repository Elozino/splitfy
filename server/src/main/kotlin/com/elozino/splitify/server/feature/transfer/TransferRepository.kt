package com.elozino.splitify.server.feature.transfer

import com.elozino.splitify.server.db.TransfersTable
import com.elozino.splitify.server.db.UsersTable
import com.elozino.splitify.server.db.dbQuery
import com.elozino.splitify.server.db.toJava
import com.elozino.splitify.server.db.toKotlin
import com.elozino.splitify.shared.domain.model.CurrencyCode
import com.elozino.splitify.shared.domain.model.GroupId
import com.elozino.splitify.shared.domain.model.Money
import com.elozino.splitify.shared.domain.model.Transfer
import com.elozino.splitify.shared.domain.model.TransferKind
import com.elozino.splitify.shared.domain.model.TransferStatus
import com.elozino.splitify.shared.domain.model.UserId
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

data class NewTransfer(
    val kind: TransferKind,
    val status: TransferStatus,
    val fromUserId: UUID,
    val toUserId: UUID,
    val amount: Money,
    val note: String?,
    val groupId: UUID?,
    val createdBy: UUID,
    val clientRequestId: String?,
    val completedAt: Instant?,
)

class TransferRepository {

    /** Two aliases of `users` so one row carries both party names. */
    private val sender: Alias<UsersTable> = UsersTable.alias("sender")
    private val recipient: Alias<UsersTable> = UsersTable.alias("recipient")

    suspend fun create(new: NewTransfer): Transfer = dbQuery {
        val id = UUID.randomUUID()
        TransfersTable.insert {
            it[TransfersTable.id] = id
            it[kind] = new.kind
            it[status] = new.status
            it[fromUserId] = new.fromUserId
            it[toUserId] = new.toUserId
            it[amount] = new.amount.amount
            it[currency] = new.amount.currency.code
            it[note] = new.note
            it[groupId] = new.groupId
            it[createdBy] = new.createdBy
            it[createdAt] = Clock.System.now().toJava()
            it[completedAt] = new.completedAt?.toJava()
            it[clientRequestId] = new.clientRequestId
        }
        requireNotNull(load(id))
    }

    suspend fun findById(id: UUID): Transfer? = dbQuery { load(id) }

    suspend fun findByClientRequestId(createdBy: UUID, key: String): Transfer? = dbQuery {
        query()
            .where { (TransfersTable.createdBy eq createdBy) and (TransfersTable.clientRequestId eq key) }
            .singleOrNull()
            ?.toTransfer()
    }

    /** Everything the user is a party to, newest first. */
    suspend fun listForUser(userId: UUID, limit: Int): List<Transfer> = dbQuery {
        query()
            .where { (TransfersTable.fromUserId eq userId) or (TransfersTable.toUserId eq userId) }
            .orderBy(TransfersTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toTransfer() }
    }

    suspend fun updateStatus(id: UUID, status: TransferStatus, completedAt: Instant?): Transfer? = dbQuery {
        TransfersTable.update({ TransfersTable.id eq id }) {
            it[TransfersTable.status] = status
            it[TransfersTable.completedAt] = completedAt?.toJava()
        }
        load(id)
    }

    private fun load(id: UUID): Transfer? =
        query().where { TransfersTable.id eq id }.singleOrNull()?.toTransfer()

    private fun query() = TransfersTable
        .join(sender, JoinType.INNER, TransfersTable.fromUserId, sender[UsersTable.id])
        .join(recipient, JoinType.INNER, TransfersTable.toUserId, recipient[UsersTable.id])
        .selectAll()

    private fun ResultRow.toTransfer(): Transfer {
        val currency = CurrencyCode(this[TransfersTable.currency])
        return Transfer(
            id = this[TransfersTable.id].toString(),
            kind = this[TransfersTable.kind],
            status = this[TransfersTable.status],
            fromUserId = UserId(this[TransfersTable.fromUserId].toString()),
            fromName = this[sender[UsersTable.displayName]],
            toUserId = UserId(this[TransfersTable.toUserId].toString()),
            toName = this[recipient[UsersTable.displayName]],
            amount = Money(this[TransfersTable.amount], currency),
            note = this[TransfersTable.note],
            groupId = this[TransfersTable.groupId]?.let { GroupId(it.toString()) },
            createdAt = this[TransfersTable.createdAt].toKotlin(),
            completedAt = this[TransfersTable.completedAt]?.toKotlin(),
        )
    }
}
