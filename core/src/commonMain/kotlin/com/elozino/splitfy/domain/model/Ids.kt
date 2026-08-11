package com.elozino.splitfy.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Identifiers are wrapped in value classes so that a [GroupId] can never be passed
 * where an [ExpenseId] is expected. They erase to `String` at runtime, so there is
 * no allocation cost and the JSON wire format stays a plain string.
 */
@Serializable
@JvmInline
value class UserId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class GroupId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class ExpenseId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class SettlementId(val value: String) {
    override fun toString(): String = value
}
