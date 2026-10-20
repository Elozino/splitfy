package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.backend.schema.*
import com.elozino.splitfy.backend.utils.Cursor
import com.elozino.splitfy.domain.model.*
import org.jetbrains.exposed.sql.*
import java.util.*
import kotlin.time.Clock

/** Raised when a debit would take the wallet below zero. */
class InsufficientFundsException(val available: Money, val requested: Money) :
    RuntimeException("Balance ${available.toPlainString()} is short of ${requested.toPlainString()}")

data class LedgerWrite(
    val userId: UUID,
    val type: LedgerEntryType,
    val amount: Money,
    val description: String,
    val counterpartyId: UUID? = null,
    val paymentMethodId: UUID? = null,
    val transferId: UUID? = null,
    val note: String? = null,
    val clientRequestId: String? = null,
)

class WalletRepository {

    suspend fun getOrCreate(userId: UUID, currency: CurrencyCode): Wallet = dbQuery {
        loadWallet(userId) ?: run {
            val now = dbNow()
            WalletsTable.insert {
                it[WalletsTable.userId] = userId
                it[WalletsTable.currency] = currency.code
                it[balance] = 0
                it[updatedAt] = now.toJava()
            }
            Wallet(UserId(userId.toString()), Money.zero(currency), now)
        }
    }

    /**
     * Applies one movement atomically.
     *
     * The wallet row is locked for the duration (`SELECT … FOR UPDATE`), so two
     * concurrent withdrawals cannot both read the same balance and each decide there
     * is enough money. The balance update and the ledger insert share one
     * transaction: there is no window in which they disagree.
     */
    suspend fun apply(write: LedgerWrite): Pair<Wallet, LedgerEntry> = dbQuery {
        write.clientRequestId?.let { key ->
            findEntryRow(write.userId, key)?.let { existing ->
                // Idempotent replay: return the original entry and the current wallet.
                return@dbQuery requireNotNull(loadWallet(write.userId)) to existing.toLedgerEntry()
            }
        }

        val locked = WalletsTable.selectAll()
            .where { WalletsTable.userId eq write.userId }
            .forUpdate()
            .singleOrNull()

        val currency = locked?.get(WalletsTable.currency)?.let(::CurrencyCode) ?: write.amount.currency
        val current = locked?.get(WalletsTable.balance) ?: 0L
        val now = dbNow()

        if (locked == null) {
            WalletsTable.insert {
                it[userId] = write.userId
                it[WalletsTable.currency] = currency.code
                it[balance] = 0
                it[updatedAt] = now.toJava()
            }
        }

        require(write.amount.currency == currency) {
            "Wallet is in ${currency.code}, entry is in ${write.amount.currency.code}"
        }

        val delta = if (write.type.isCredit) write.amount.amount else -write.amount.amount
        val updated = current + delta
        if (updated < 0) {
            throw InsufficientFundsException(Money(current, currency), write.amount)
        }

        WalletsTable.update({ WalletsTable.userId eq write.userId }) {
            it[balance] = updated
            it[updatedAt] = now.toJava()
        }

        val entryId = UUID.randomUUID()
        LedgerEntriesTable.insert {
            it[id] = entryId
            it[userId] = write.userId
            it[type] = write.type
            it[amount] = write.amount.amount
            it[LedgerEntriesTable.currency] = currency.code
            it[balanceAfter] = updated
            it[description] = write.description
            it[counterpartyId] = write.counterpartyId
            it[paymentMethodId] = write.paymentMethodId
            it[transferId] = write.transferId
            it[note] = write.note
            it[occurredAt] = now.toJava()
            it[clientRequestId] = write.clientRequestId
        }

        val wallet = Wallet(UserId(write.userId.toString()), Money(updated, currency), now)
        val entry = requireNotNull(loadEntry(entryId))
        wallet to entry
    }

    suspend fun ledger(
        userId: UUID,
        cursor: Cursor?,
        limit: Int,
        types: List<LedgerEntryType>,
    ): List<LedgerEntry> = dbQuery {
        entriesQuery()
            .where {
                var condition = LedgerEntriesTable.userId eq userId
                if (types.isNotEmpty()) condition = condition and (LedgerEntriesTable.type inList types)
                if (cursor != null) {
                    condition = condition and (
                            (LedgerEntriesTable.occurredAt less cursor.createdAt.toJava()) or
                                    ((LedgerEntriesTable.occurredAt eq cursor.createdAt.toJava()) and
                                            (LedgerEntriesTable.id less cursor.id))
                            )
                }
                condition
            }
            .orderBy(LedgerEntriesTable.occurredAt to SortOrder.DESC, LedgerEntriesTable.id to SortOrder.DESC)
            .limit(limit + 1)
            .map { it.toLedgerEntry() }
    }

    suspend fun findByClientRequestId(userId: UUID, key: String): LedgerEntry? = dbQuery {
        findEntryRow(userId, key)?.toLedgerEntry()
    }

    // --- helpers, always called inside a transaction ------------------------

    private fun loadWallet(userId: UUID): Wallet? =
        WalletsTable.selectAll().where { WalletsTable.userId eq userId }.singleOrNull()?.let { row ->
            Wallet(
                userId = UserId(userId.toString()),
                balance = Money(row[WalletsTable.balance], CurrencyCode(row[WalletsTable.currency])),
                updatedAt = row[WalletsTable.updatedAt].toKotlin(),
            )
        }

    private fun loadEntry(id: UUID): LedgerEntry? =
        entriesQuery().where { LedgerEntriesTable.id eq id }.singleOrNull()?.toLedgerEntry()

    private fun findEntryRow(userId: UUID, key: String): ResultRow? =
        entriesQuery()
            .where {
                (LedgerEntriesTable.userId eq userId) and (LedgerEntriesTable.clientRequestId eq key)
            }
            .singleOrNull()

    /** Joins the counterparty and payment method so a statement row renders in one query. */
    private fun entriesQuery() = LedgerEntriesTable
        .join(UsersTable, JoinType.LEFT, LedgerEntriesTable.counterpartyId, UsersTable.id)
        .join(PaymentMethodsTable, JoinType.LEFT, LedgerEntriesTable.paymentMethodId, PaymentMethodsTable.id)
        .selectAll()
}

internal fun ResultRow.toLedgerEntry(): LedgerEntry {
    val currency = CurrencyCode(this[LedgerEntriesTable.currency])
    return LedgerEntry(
        id = this[LedgerEntriesTable.id].toString(),
        type = this[LedgerEntriesTable.type],
        amount = Money(this[LedgerEntriesTable.amount], currency),
        balanceAfter = Money(this[LedgerEntriesTable.balanceAfter], currency),
        description = this[LedgerEntriesTable.description],
        counterpartyName = getOrNull(UsersTable.displayName),
        paymentMethodLabel = getOrNull(PaymentMethodsTable.label),
        paymentMethodBrand = getOrNull(PaymentMethodsTable.brand),
        note = this[LedgerEntriesTable.note],
        occurredAt = this[LedgerEntriesTable.occurredAt].toKotlin(),
    )
}
