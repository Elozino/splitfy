package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.dto.NotificationCategory
import com.elozino.splitfy.api.dto.PushData
import com.elozino.splitfy.backend.repositories.ExpenseRepository
import com.elozino.splitfy.backend.repositories.GroupRepository
import com.elozino.splitfy.backend.repositories.JobRunRepository
import com.elozino.splitfy.backend.repositories.NotificationPreferencesRepository
import com.elozino.splitfy.backend.repositories.PaymentReminderRepository
import com.elozino.splitfy.backend.repositories.SettlementRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.schema.GroupsTable
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.utils.AppConfig
import com.elozino.splitfy.domain.model.Expense
import com.elozino.splitfy.domain.model.Settlement
import com.elozino.splitfy.domain.model.SettlementStatus
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.domain.split.BalanceCalculator
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

/**
 * Once a day, nudges everyone who has owed money in a group for longer than
 * [AppConfig.PushConfig.reminderAfterDays].
 *
 * Two guards keep it from double-sending: the day is claimed in `job_runs` before
 * anything is sent, so only one instance runs it; and each `(debtor, group)` is
 * reminded at most once every [AppConfig.PushConfig.reminderEveryDays], so a re-run
 * on another day does not repeat yesterday's nudge.
 */
class PaymentReminderJob(
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val settlements: SettlementRepository,
    private val users: UserRepository,
    private val reminders: PaymentReminderRepository,
    private val runs: JobRunRepository,
    private val preferences: NotificationPreferencesRepository,
    private val notifications: NotificationService,
    private val config: AppConfig.PushConfig,
    private val clock: Clock = Clock.System,
) {
    private val logger = LoggerFactory.getLogger(PaymentReminderJob::class.java)

    /** Runs today's reminders unless another instance already has. Returns how many were sent. */
    suspend fun runOnce(day: LocalDate = clock.now().toJava().atZone(ZoneOffset.UTC).toLocalDate()): Int {
        if (!runs.claim(JOB_NAME, day)) {
            logger.info("Payment reminders for {} already claimed by another run", day)
            return 0
        }
        val now = clock.now()
        var sent = 0
        for (groupId in activeGroupIds()) {
            sent += runCatching { remindGroup(groupId, now) }
                .onFailure { logger.warn("Payment reminders failed for group {}: {}", groupId, it.message) }
                .getOrDefault(0)
        }
        logger.info("Sent {} payment reminders for {}", sent, day)
        return sent
    }

    private suspend fun remindGroup(groupId: UUID, now: Instant): Int {
        val group = groups.findById(groupId) ?: return 0
        val ledger = expenses.allForGroup(groupId)
        val payments = settlements.listForGroup(groupId)
        val balances = BalanceCalculator.balances(group.currency, ledger, payments)
        val transfers = BalanceCalculator.simplify(balances)
        val since = owingSince(ledger, payments)
        val threshold = now - config.reminderAfterDays.days
        val memberIds = group.members.map { it.userId }.toSet()

        var sent = 0
        for (balance in balances) {
            if (!balance.owes || balance.userId !in memberIds) continue
            val owingFrom = since[balance.userId] ?: continue
            if (owingFrom > threshold) continue
            // They have said they paid; let the payee confirm rather than nagging.
            if (payments.any { it.fromUserId == balance.userId && it.status == SettlementStatus.PENDING }) continue

            val debtor = UUID.fromString(balance.userId.value)
            if (!preferences.get(debtor).paymentReminders) continue
            val last = reminders.lastSent(debtor, groupId)
            if (last != null && last.plusSeconds(config.reminderEveryDays.days.inWholeSeconds) > now.toJava()) continue

            val owed = balance.net.abs()
            val creditors = transfers.filter { it.from == balance.userId }.map { it.to }
            val to = when (creditors.size) {
                1 -> users.findById(UUID.fromString(creditors.single().value))?.displayName
                else -> null
            }
            notifications.notify(
                userId = debtor,
                category = NotificationCategory.PAYMENT,
                title = "You owe ${owed.display()} in ${group.name}",
                message = if (to != null) "Settle up with $to when you can." else "Settle up with the group when you can.",
                topic = PushTopic.REMINDERS,
                data = mapOf(PushData.GROUP_ID to group.id.value, PushData.TYPE to "reminder"),
            )
            reminders.markSent(debtor, groupId, now.toJava())
            sent++
        }
        return sent
    }

    private suspend fun activeGroupIds(): List<UUID> = dbQuery {
        GroupsTable.selectAll()
            .where { GroupsTable.archivedAt.isNull() }
            .map { it[GroupsTable.id] }
    }

    companion object {
        const val JOB_NAME = "payment_reminders"

        /**
         * When each member's balance last went from settled (or owed) to owing,
         * replaying the ledger in order. Members not currently owing are absent.
         */
        internal fun owingSince(expenses: List<Expense>, settlements: List<Settlement>): Map<UserId, Instant> {
            data class Event(val at: Instant, val deltas: List<Pair<UserId, Long>>)

            val events = expenses.filterNot { it.isDeleted }.map { expense ->
                Event(
                    at = expense.createdAt,
                    deltas = listOf(expense.paidBy to expense.total.amount) +
                        expense.shares.map { it.userId to -it.amount.amount },
                )
            } + settlements.filter { it.status == SettlementStatus.CONFIRMED }.map { settlement ->
                Event(
                    at = settlement.settledAt,
                    deltas = listOf(
                        settlement.fromUserId to settlement.amount.amount,
                        settlement.toUserId to -settlement.amount.amount,
                    ),
                )
            }

            val net = mutableMapOf<UserId, Long>()
            val since = mutableMapOf<UserId, Instant>()
            for (event in events.sortedBy { it.at }) {
                val touched = event.deltas.map { it.first }.toSet()
                event.deltas.forEach { (user, delta) -> net[user] = (net[user] ?: 0L) + delta }
                for (user in touched) {
                    val owing = (net[user] ?: 0L) < 0
                    when {
                        owing && user !in since -> since[user] = event.at
                        !owing -> since.remove(user)
                    }
                }
            }
            return since
        }
    }
}
