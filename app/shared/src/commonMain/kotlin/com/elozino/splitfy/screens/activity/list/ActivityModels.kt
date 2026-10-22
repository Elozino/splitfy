package com.elozino.splitfy.screens.activity.list

import com.elozino.splitfy.FeatureFlags
import com.elozino.splitfy.api.dto.DebtEntry

data class ActivitySection(val date: String, val items: List<ActivityItemData>)

/** Which way a debt runs relative to the viewer; picks the amount colour (docs/THEMING.md). */
enum class DebtDirection { OWED_TO_YOU, YOU_OWE, BETWEEN_OTHERS }

data class ActivityItemData(
    val name: String,
    val status: String,
    val target: String,
    val amount: String,
    /** Null when the viewer is neither party: the server only records settlements you are part of. */
    val actionLabel: String?,
    val direction: DebtDirection = DebtDirection.BETWEEN_OTHERS,
    val isCompleted: Boolean = false,
    val groupId: String = "",
)

/** Turns a debt line into the row model, phrased from the viewer's point of view. */
internal fun DebtEntry.toItem(currentUserId: String?, walletEnabled: Boolean = FeatureFlags.WALLET): ActivityItemData {
    val youArePayer = fromUserId.value == currentUserId
    val youArePayee = toUserId.value == currentUserId
    return ActivityItemData(
        name = if (youArePayer) "$fromName (You)" else fromName,
        status = "owes",
        target = if (youArePayee) "$toName (You)" else toName,
        amount = amount.toPlainString() + " " + amount.currency.code,
        actionLabel = when {
            isSettled -> "Paid"
            !youArePayer && !youArePayee -> null
            !walletEnabled -> "Settle"
            youArePayer -> "Pay"
            else -> "Request"
        },
        direction = when {
            youArePayee -> DebtDirection.OWED_TO_YOU
            youArePayer -> DebtDirection.YOU_OWE
            else -> DebtDirection.BETWEEN_OTHERS
        },
        isCompleted = isSettled,
        groupId = groupId.value,
    )
}
