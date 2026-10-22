package com.elozino.splitfy.screens.activity

import com.elozino.splitfy.api.dto.DebtEntry
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.GroupId
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.screens.activity.list.DebtDirection
import com.elozino.splitfy.screens.activity.list.toItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** How an activity row reads, and what it offers, depending on who is looking. */
class ActivityItemMappingTest {

    private fun debt(from: String, to: String, isSettled: Boolean = false) = DebtEntry(
        fromUserId = UserId(from),
        fromName = from.replaceFirstChar { it.uppercase() },
        toUserId = UserId(to),
        toName = to.replaceFirstChar { it.uppercase() },
        amount = Money(180_000, CurrencyCode.NGN),
        groupId = GroupId("g"),
        groupName = "Lagos weekend",
        isSettled = isSettled,
        lastActivityAt = Instant.fromEpochSeconds(0),
    )

    @Test
    fun `a debt owed to the viewer is positive and offers a request`() {
        val item = debt(from = "chi", to = "ada").toItem("ada", walletEnabled = true)

        assertEquals(DebtDirection.OWED_TO_YOU, item.direction)
        assertEquals("Ada (You)", item.target)
        assertEquals("Request", item.actionLabel)
    }

    @Test
    fun `a debt the viewer owes is negative and offers a payment`() {
        val item = debt(from = "ada", to = "bayo").toItem("ada", walletEnabled = true)

        assertEquals(DebtDirection.YOU_OWE, item.direction)
        assertEquals("Ada (You)", item.name)
        assertEquals("Pay", item.actionLabel)
    }

    @Test
    fun `without a wallet either party settles`() {
        assertEquals("Settle", debt(from = "chi", to = "ada").toItem("ada", walletEnabled = false).actionLabel)
        assertEquals("Settle", debt(from = "ada", to = "chi").toItem("ada", walletEnabled = false).actionLabel)
    }

    @Test
    fun `a debt between two other members is neutral and offers no action`() {
        listOf(true, false).forEach { wallet ->
            val item = debt(from = "chi", to = "bayo").toItem("ada", walletEnabled = wallet)

            assertEquals(DebtDirection.BETWEEN_OTHERS, item.direction)
            assertNull(item.actionLabel)
        }
    }

    @Test
    fun `a settled debt reads as paid for everyone`() {
        assertEquals("Paid", debt(from = "chi", to = "bayo", isSettled = true).toItem("ada").actionLabel)
        assertEquals("Paid", debt(from = "chi", to = "ada", isSettled = true).toItem("ada").actionLabel)
    }
}
