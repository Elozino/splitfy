package com.elozino.splitify.backend.utils

import com.elozino.splitify.api.dto.Page
import io.ktor.server.application.ApplicationCall
import java.util.Base64
import java.util.UUID
import kotlin.time.Instant

/**
 * Keyset ("seek") pagination.
 *
 * Offsets shift under the reader whenever a row is inserted above them — the very
 * thing that happens while a group is actively adding expenses. Anchoring on
 * `(created_at, id)` keeps an infinite scroll stable.
 */
data class Cursor(val createdAt: Instant, val id: UUID) {

    fun encode(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("${createdAt.toEpochMilliseconds()}:$id".toByteArray())

    companion object {
        fun decode(raw: String?): Cursor? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val decoded = String(Base64.getUrlDecoder().decode(raw))
                val (millis, id) = decoded.split(':', limit = 2)
                Cursor(Instant.fromEpochMilliseconds(millis.toLong()), UUID.fromString(id))
            }.getOrElse {
                throw AppException.BadRequest("Malformed pagination cursor")
            }
        }
    }
}

/** Reads `?limit=` and clamps it so a client cannot ask for the whole table. */
fun ApplicationCall.paginationLimit(): Int =
    request.queryParameters["limit"]?.toIntOrNull()
        ?.coerceIn(1, Page.MAX_LIMIT)
        ?: Page.DEFAULT_LIMIT

fun ApplicationCall.paginationCursor(): Cursor? = Cursor.decode(request.queryParameters["cursor"])

/**
 * Builds a page from `limit + 1` fetched rows: the extra row tells us whether a
 * next page exists without a second `COUNT(*)` query.
 */
fun <T> buildPage(rows: List<T>, limit: Int, cursorOf: (T) -> Cursor): Page<T> {
    val hasMore = rows.size > limit
    val items = if (hasMore) rows.take(limit) else rows
    return Page(
        items = items,
        nextCursor = if (hasMore) items.lastOrNull()?.let(cursorOf)?.encode() else null,
        hasMore = hasMore,
    )
}

/** Parses a path parameter that must be a UUID, or fails with a 400. */
fun ApplicationCall.uuidParam(name: String): UUID {
    val raw = parameters[name] ?: throw AppException.BadRequest("Missing path parameter '$name'")
    return runCatching { UUID.fromString(raw) }
        .getOrElse { throw AppException.BadRequest("'$name' is not a valid identifier") }
}
