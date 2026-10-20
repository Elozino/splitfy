package com.elozino.splitfy.screens.activity.list

data class ActivitySection(val date: String, val items: List<ActivityItemData>)

data class ActivityItemData(
    val name: String,
    val status: String,
    val target: String,
    val amount: String,
    val actionLabel: String,
    val isOwed: Boolean = false,
    val isCompleted: Boolean = false,
    val groupId: String = "",
)

