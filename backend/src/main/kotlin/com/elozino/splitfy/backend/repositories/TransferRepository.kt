package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.backend.schema.*
import com.elozino.splitfy.domain.model.*
import org.jetbrains.exposed.sql.*
import java.util.*
import kotlin.time.Clock
import kotlin.time.Instant

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

    /**
     * Moves a transfer from [from] to [to] in one conditional UPDATE, so a concurrent
     * approve and decline can't both win. Returns null when the row was no longer in [from].
     */
    suspend fun transition(
        id: UUID,
        from: TransferStatus,
        to: TransferStatus,
        completedAt: Instant?,
    ): Transfer? = dbQuery {
        val updated = TransfersTable.update({ (TransfersTable.id eq id) and (TransfersTable.status eq from) }) {
            it[status] = to
            it[TransfersTable.completedAt] = completedAt?.toJava()
        }
        if (updated == 0) null else load(id)
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
