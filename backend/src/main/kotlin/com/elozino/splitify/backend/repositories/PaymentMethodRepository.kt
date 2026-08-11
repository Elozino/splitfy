package com.elozino.splitify.backend.repositories

import com.elozino.splitify.api.dto.AddPaymentMethodRequest
import com.elozino.splitify.backend.schema.dbQuery
import com.elozino.splitify.backend.schema.PaymentMethodsTable
import com.elozino.splitify.backend.schema.toJava
import com.elozino.splitify.backend.schema.toKotlin
import com.elozino.splitify.domain.model.PaymentMethod
import java.util.UUID
import kotlin.time.Clock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update

class PaymentMethodRepository {

    suspend fun list(userId: UUID): List<PaymentMethod> = dbQuery {
        PaymentMethodsTable.selectAll()
            .where { PaymentMethodsTable.userId eq userId }
            .orderBy(
                PaymentMethodsTable.isDefault to SortOrder.DESC,
                PaymentMethodsTable.createdAt to SortOrder.ASC,
            )
            .map { it.toPaymentMethod() }
    }

    suspend fun find(userId: UUID, id: UUID): PaymentMethod? = dbQuery {
        PaymentMethodsTable.selectAll()
            .where { (PaymentMethodsTable.id eq id) and (PaymentMethodsTable.userId eq userId) }
            .singleOrNull()
            ?.toPaymentMethod()
    }

    suspend fun add(userId: UUID, request: AddPaymentMethodRequest): PaymentMethod = dbQuery {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        // The first method a user adds is their default, whatever they asked for.
        val isFirst = PaymentMethodsTable.selectAll()
            .where { PaymentMethodsTable.userId eq userId }
            .limit(1)
            .none()
        val makeDefault = request.makeDefault || isFirst
        if (makeDefault) clearDefault(userId)

        PaymentMethodsTable.insert {
            it[PaymentMethodsTable.id] = id
            it[PaymentMethodsTable.userId] = userId
            it[type] = request.type
            it[label] = request.label.trim()
            it[brand] = request.brand
            it[last4] = request.last4
            it[providerToken] = request.providerToken
            it[isDefault] = makeDefault
            it[createdAt] = now.toJava()
        }
        PaymentMethodsTable.selectAll()
            .where { PaymentMethodsTable.id eq id }
            .single()
            .toPaymentMethod()
    }

    suspend fun makeDefault(userId: UUID, id: UUID): Boolean = dbQuery {
        val owned = PaymentMethodsTable.selectAll()
            .where { (PaymentMethodsTable.id eq id) and (PaymentMethodsTable.userId eq userId) }
            .any()
        if (!owned) return@dbQuery false

        clearDefault(userId)
        PaymentMethodsTable.update({ PaymentMethodsTable.id eq id }) {
            it[isDefault] = true
        } > 0
    }

    suspend fun delete(userId: UUID, id: UUID): Boolean = dbQuery {
        val removed = PaymentMethodsTable.deleteWhere {
            (PaymentMethodsTable.id eq id) and (PaymentMethodsTable.userId eq userId)
        } > 0

        // Never leave the user without a default while they still have methods.
        if (removed) {
            val hasDefault = PaymentMethodsTable.selectAll()
                .where { (PaymentMethodsTable.userId eq userId) and (PaymentMethodsTable.isDefault eq true) }
                .any()
            if (!hasDefault) {
                PaymentMethodsTable.selectAll()
                    .where { PaymentMethodsTable.userId eq userId }
                    .orderBy(PaymentMethodsTable.createdAt to SortOrder.ASC)
                    .firstOrNull()
                    ?.let { row ->
                        PaymentMethodsTable.update({ PaymentMethodsTable.id eq row[PaymentMethodsTable.id] }) {
                            it[isDefault] = true
                        }
                    }
            }
        }
        removed
    }

    private fun clearDefault(userId: UUID) {
        PaymentMethodsTable.update({
            (PaymentMethodsTable.userId eq userId) and (PaymentMethodsTable.isDefault eq true)
        }) {
            it[isDefault] = false
        }
    }
}

private fun ResultRow.toPaymentMethod() = PaymentMethod(
    id = this[PaymentMethodsTable.id].toString(),
    type = this[PaymentMethodsTable.type],
    label = this[PaymentMethodsTable.label],
    brand = this[PaymentMethodsTable.brand],
    last4 = this[PaymentMethodsTable.last4],
    isDefault = this[PaymentMethodsTable.isDefault],
    createdAt = this[PaymentMethodsTable.createdAt].toKotlin(),
)
