package com.elozino.splitify

import com.elozino.splitify.api.dto.Contact
import com.elozino.splitify.domain.model.Money
import com.elozino.splitify.domain.model.UserId

/**
 * A counterparty as the pay/request screens render them.
 *
 * Built from a real [Contact]; [amount] is the outstanding balance with that person,
 * so tapping a row proposes settling exactly what is owed.
 */
data class TransactionUser(
    val userId: UserId,
    val name: String,
    val email: String,
    val balance: Money,
    val owesYou: Boolean,
) {
    val amount: String get() = balance.abs().toPlainString() + " " + balance.currency.code

    companion object {
        fun from(contact: Contact): TransactionUser? {
            val balance = contact.netBalance ?: return null
            if (balance.isZero) return null
            return TransactionUser(
                userId = contact.userId,
                name = contact.displayName,
                email = contact.email,
                balance = balance,
                // Positive means the contact owes the signed-in user.
                owesYou = balance.isPositive,
            )
        }
    }
}
