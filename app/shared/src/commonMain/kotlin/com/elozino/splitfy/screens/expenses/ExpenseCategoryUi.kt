package com.elozino.splitfy.screens.expenses

import com.elozino.splitfy.domain.model.ExpenseCategory

/** The glyph every expense surface (list, detail, add form) shows for a category. */
internal fun ExpenseCategory.emoji(): String = when (this) {
    ExpenseCategory.FOOD -> "🍱"
    ExpenseCategory.TRANSPORT -> "🚕"
    ExpenseCategory.ACCOMMODATION -> "🏨"
    ExpenseCategory.GROCERIES -> "🛒"
    ExpenseCategory.ENTERTAINMENT -> "🎟️"
    ExpenseCategory.UTILITIES -> "💡"
    ExpenseCategory.SHOPPING -> "🛍️"
    ExpenseCategory.HEALTH -> "🩺"
    ExpenseCategory.OTHER -> "🧾"
}

internal fun ExpenseCategory.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
