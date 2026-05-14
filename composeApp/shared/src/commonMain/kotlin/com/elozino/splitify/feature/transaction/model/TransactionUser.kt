package com.elozino.splitify.feature.transaction.model

/**
 * Data model for a user in the transaction flow.
 */
data class TransactionUser(
    val name: String,
    val email: String,
    val amount: String,
    val date: String = "Dec 20, 2023 - 09:41 AM"
)
