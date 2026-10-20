package com.elozino.splitfy.backend.payments

import com.elozino.splitfy.backend.schema.UsersTable
import com.elozino.splitfy.backend.schema.LedgerEntriesTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

internal object PaymentIntents : Table("payment_intents") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val kind = enumerationByName<PaymentKind>("kind", 16)
    val amount = long("amount")
    val currency = char("currency", 3)
    val reference = varchar("provider_reference", 80).uniqueIndex()
    val requestId = varchar("client_request_id", 64)
    val destination = varchar("destination", 254)
    val status = enumerationByName<PaymentStatus>("status", 16)
    val createdAt = timestamp("created_at")
    val settledAt = timestamp("settled_at").nullable()
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(userId, requestId) }
}

internal object PaymentEffects : Table("payment_effects") {
    val intentId = uuid("intent_id").references(PaymentIntents.id)
    val effect = varchar("effect", 16)
    val ledgerId = uuid("ledger_entry_id").references(LedgerEntriesTable.id).uniqueIndex()
    override val primaryKey = PrimaryKey(intentId, effect)
}
