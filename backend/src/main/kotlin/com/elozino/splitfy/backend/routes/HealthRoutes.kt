package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.routing.application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class HealthResponse(
    val status: String,
    val checks: Map<String, String> = emptyMap(),
)

/**
 * Liveness and readiness probes.
 *
 * `/health` answers "is the process alive" — it must never touch a dependency, or a
 * database blip would make the orchestrator kill otherwise-healthy pods.
 * `/ready` answers "can this instance serve traffic", which does require the
 * database, so a failing instance is pulled from the load balancer instead.
 *
 * On SIGTERM `/ready` starts answering 503 straight away, while the engine's grace
 * period lets in-flight requests finish: the load balancer stops sending new
 * traffic before the connections close.
 */
fun Route.healthRoutes(database: Database) {
    val logger = LoggerFactory.getLogger("HealthCheck")
    val draining = AtomicBoolean(false)
    application.monitor.subscribe(ApplicationStopPreparing) { draining.set(true) }

    get(ApiRoutes.System.HEALTH) {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "UP"))
    }

    get(ApiRoutes.System.READY) {
        if (draining.get()) {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse(status = "DRAINING"))
            return@get
        }
        val databaseUp = runCatching {
            withContext(Dispatchers.IO) {
                transaction(database) { exec("SELECT 1") {} }
            }
            true
        }.getOrElse {
            logger.warn("Readiness probe failed: database unreachable", it)
            false
        }

        val response = HealthResponse(
            status = if (databaseUp) "UP" else "DOWN",
            checks = mapOf("database" to if (databaseUp) "UP" else "DOWN"),
        )
        call.respond(
            if (databaseUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            response,
        )
    }
}
