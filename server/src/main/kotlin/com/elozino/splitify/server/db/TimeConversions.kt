package com.elozino.splitify.server.db

import kotlin.time.Instant

/**
 * Bridges the persistence layer's `java.time.Instant` and the domain's
 * `kotlin.time.Instant` (which `:shared` uses because it is multiplatform).
 *
 * The conversion is exact — both types are an epoch second plus a nanosecond
 * adjustment — and confined to the repositories, so nothing above the data layer
 * ever sees a JDBC type.
 */
internal fun java.time.Instant.toKotlin(): Instant =
    Instant.fromEpochSeconds(epochSecond, nano)

internal fun Instant.toJava(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
