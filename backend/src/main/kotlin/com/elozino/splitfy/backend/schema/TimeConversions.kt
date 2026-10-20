package com.elozino.splitfy.backend.schema

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Bridges the persistence layer's `java.time.Instant` and the domain's
 * `kotlin.time.Instant` (which `:shared` uses because it is multiplatform).
 *
 * Both types are an epoch second plus a nanosecond adjustment; writes are truncated
 * to the database's microsecond precision (see [toJava]). Confined to the
 * repositories, so nothing above the data layer ever sees a JDBC type.
 */
internal fun java.time.Instant.toKotlin(): Instant =
    Instant.fromEpochSeconds(epochSecond, nano)

/**
 * Truncated to microseconds, PostgreSQL's TIMESTAMP precision, so a value written
 * is exactly the value read back. Linux clocks report nanoseconds; without this a
 * freshly created row compared unequal to the same row loaded a moment later.
 */
internal fun Instant.toJava(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, (nanosecondsOfSecond / 1_000 * 1_000).toLong())

/** "Now" at the precision the database stores, for values returned without a round trip. */
internal fun dbNow(): Instant = Clock.System.now().let { Instant.fromEpochSeconds(it.epochSeconds, it.nanosecondsOfSecond / 1_000 * 1_000) }
