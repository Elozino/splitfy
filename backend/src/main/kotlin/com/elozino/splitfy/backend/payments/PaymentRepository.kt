package com.elozino.splitfy.backend.payments

import com.elozino.splitfy.backend.schema.*
import com.elozino.splitfy.domain.model.LedgerEntryType
import com.elozino.splitfy.domain.model.PaymentMethodType
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.*

class PaymentRepository {
    /** Serialize intent creation per user, including first-wallet creation and idempotency replays. */
    suspend fun create(user: UUID, kind: PaymentKind, amount: Long, currency: String, key: String, destination: String): PaymentIntent = dbQuery {
        require(amount > 0 && currency == "NGN")
        require(key.length in 1..64 && destination.length in 1..254)
        UsersTable.selectAll().where { UsersTable.id eq user }.forUpdate().single()
        PaymentIntents.selectAll().where { (PaymentIntents.userId eq user) and (PaymentIntents.requestId eq key) }
            .singleOrNull()?.let {
                val existing = it.intent()
                require(existing.kind == kind && existing.amount == amount && existing.currency == currency && existing.destination == destination) {
                    "Idempotency key already used with different parameters"
                }
                return@dbQuery existing
            }
        WalletsTable.insertIgnore {
            it[userId] = user; it[WalletsTable.currency] = currency; it[balance] = 0; it[updatedAt] = Instant.now()
        }
        val id = UUID.randomUUID()
        val intent = PaymentIntent(id, user, kind, amount, currency, "splitfy-${id}", destination, PaymentStatus.PENDING)
        PaymentIntents.insert {
            it[PaymentIntents.id] = id; it[userId] = user; it[PaymentIntents.kind] = kind
            it[PaymentIntents.amount] = amount; it[PaymentIntents.currency] = currency
            it[reference] = intent.reference; it[requestId] = key; it[PaymentIntents.destination] = destination
            it[status] = PaymentStatus.PENDING; it[createdAt] = Instant.now()
        }
        if (kind == PaymentKind.WITHDRAWAL) movement(intent, "RESERVE", false)
        intent
    }

    suspend fun find(reference: String): PaymentIntent? = dbQuery {
        PaymentIntents.selectAll().where { PaymentIntents.reference eq reference }.singleOrNull()?.intent()
    }

    /** Read-only reconciliation queue. Polling must never credit a top-up. */
    suspend fun pending(limit: Int = 100): List<PaymentIntent> = dbQuery {
        require(limit in 1..1000)
        PaymentIntents.selectAll().where { PaymentIntents.status eq PaymentStatus.PENDING }
            .orderBy(PaymentIntents.createdAt).limit(limit).map { it.intent() }
    }

    internal suspend fun apply(event: VerifiedEvent): Boolean = dbQuery {
        val row = PaymentIntents.selectAll().where { PaymentIntents.reference eq event.observation.reference }
            .forUpdate().singleOrNull() ?: return@dbQuery false
        val intent = row.intent()
        val observation = event.observation
        require(intent.amount == observation.amount && intent.currency == observation.currency) { "Payment amount/currency mismatch" }
        val next = when (event.type) {
            "charge.success" -> {
                require(intent.kind == PaymentKind.TOP_UP && observation.status == "success")
                require(intent.destination.equals(event.customerEmail, ignoreCase = true)) { "Payment customer mismatch" }
                PaymentStatus.COMPLETED
            }
            "transfer.success" -> {
                require(intent.kind == PaymentKind.WITHDRAWAL && observation.status == "success")
                PaymentStatus.COMPLETED
            }
            "transfer.failed", "transfer.reversed" -> {
                require(intent.kind == PaymentKind.WITHDRAWAL)
                require(observation.status == if (event.type == "transfer.failed") "failed" else "reversed")
                PaymentStatus.REVERSED
            }
            else -> return@dbQuery false
        }
        // Reversal wins over a late success. A failed delivery cannot undo a completed transfer;
        // an explicit reversal can, and has exactly one compensating credit.
        if (intent.status == next || intent.status == PaymentStatus.REVERSED) return@dbQuery true
        if (intent.status == PaymentStatus.COMPLETED && event.type != "transfer.reversed") return@dbQuery true
        if (intent.kind == PaymentKind.TOP_UP) {
            movement(intent, "CREDIT", true)
            saveAuthorization(intent, event.authorization)
        } else if (next == PaymentStatus.REVERSED) movement(intent, "RELEASE", true)
        PaymentIntents.update({ PaymentIntents.id eq intent.id }) {
            it[status] = next; it[settledAt] = Instant.now()
        }
        true
    }

    /** Called only inside the same transaction as the locked intent transition. */
    private fun movement(intent: PaymentIntent, effect: String, credit: Boolean) {
        val wallet = WalletsTable.selectAll().where { WalletsTable.userId eq intent.userId }.forUpdate().single()
        require(wallet[WalletsTable.currency] == intent.currency)
        val balance = if (credit) Math.addExact(wallet[WalletsTable.balance], intent.amount)
            else Math.subtractExact(wallet[WalletsTable.balance], intent.amount)
        require(balance >= 0) { "Insufficient funds" }
        val now = Instant.now()
        WalletsTable.update({ WalletsTable.userId eq intent.userId }) { it[WalletsTable.balance] = balance; it[updatedAt] = now }
        val ledgerId = UUID.randomUUID()
        LedgerEntriesTable.insert {
            it[id] = ledgerId; it[userId] = intent.userId
            // Reversals are credits; their exact reason and intent are retained in payment_effects.
            it[type] = if (credit) LedgerEntryType.TOP_UP else LedgerEntryType.WITHDRAWAL
            it[amount] = intent.amount; it[currency] = intent.currency; it[balanceAfter] = balance
            it[description] = when (effect) { "RESERVE" -> "Sandbox withdrawal pending"; "RELEASE" -> "Sandbox withdrawal reversed"; else -> "Sandbox top up" }
            it[occurredAt] = now
        }
        PaymentEffects.insert { it[intentId] = intent.id; it[PaymentEffects.effect] = effect; it[PaymentEffects.ledgerId] = ledgerId }
    }

    private fun saveAuthorization(intent: PaymentIntent, authorization: JsonObject?) {
        if (authorization?.get("reusable")?.jsonPrimitive?.booleanOrNull != true) return
        val code = authorization["authorization_code"]?.jsonPrimitive?.content ?: return
        val last4 = authorization["last4"]?.jsonPrimitive?.content ?: return
        if (!code.startsWith("AUTH_") || code.length > 255 || !last4.matches(Regex("[0-9]{4}"))) return
        // Locking the wallet above serializes successful charges for this user.
        if (PaymentMethodsTable.selectAll().where {
                (PaymentMethodsTable.userId eq intent.userId) and (PaymentMethodsTable.providerToken eq code)
            }.any()) return
        PaymentMethodsTable.insert {
            it[id] = UUID.randomUUID(); it[userId] = intent.userId; it[type] = PaymentMethodType.CARD
            it[label] = "Paystack sandbox card"; it[brand] = authorization["brand"]?.jsonPrimitive?.content?.take(32)
            it[PaymentMethodsTable.last4] = last4; it[providerToken] = code; it[isDefault] = false; it[createdAt] = Instant.now()
        }
    }
}

private fun ResultRow.intent() = PaymentIntent(
    this[PaymentIntents.id], this[PaymentIntents.userId], this[PaymentIntents.kind], this[PaymentIntents.amount],
    this[PaymentIntents.currency], this[PaymentIntents.reference], this[PaymentIntents.destination], this[PaymentIntents.status],
)
