package com.elozino.splitfy.backend.plugins

import com.elozino.splitfy.backend.services.PaymentReminderJob
import com.elozino.splitfy.backend.utils.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.log
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

/**
 * Runs the daily payment reminder inside the API process.
 *
 * Every instance schedules it; the job's claim in `job_runs` makes exactly one of
 * them do the work. An instance that boots after today's hour runs it straight
 * away, so a deploy across the hour does not skip a day. The loop lives on the
 * application's scope and stops with it.
 */
fun Application.configureScheduling(config: AppConfig.PushConfig) {
    if (!config.remindersEnabled) return
    val job by inject<PaymentReminderJob>()

    launch {
        while (true) {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val todayAt = now.withHour(config.reminderHourUtc).withMinute(0).withSecond(0).withNano(0)
            if (!now.isBefore(todayAt)) {
                try {
                    job.runOnce(now.toLocalDate())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Payment reminder run failed: {}", e.message)
                }
            }
            val next = if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
            delay(Duration.between(ZonedDateTime.now(ZoneOffset.UTC), next).toMillis().coerceAtLeast(1_000))
        }
    }
}
