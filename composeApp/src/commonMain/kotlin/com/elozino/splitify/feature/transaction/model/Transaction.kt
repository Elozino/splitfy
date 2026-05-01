package com.elozino.splitify.feature.transaction.model

data class Transaction(
    val id: String,
    val type: TransactionType,
    val title: String,
    val subtitle: String,
    val amount: String,
    val date: String,
    val iconUrl: String? = null,
    val brandIcon: String? = null,
    val details: Map<String, String> = emptyMap(),
    val notes: String? = null
)
