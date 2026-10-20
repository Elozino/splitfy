package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.backend.plugins.PiiScrubber
import com.elozino.splitfy.backend.plugins.redactPath
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservabilityTest {

    private val database = TestDatabase("splitfy_observability")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `metrics are hidden unless a token is configured`() = testApplication {
        useTestConfig(database)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.System.METRICS).status)
    }

    @Test
    fun `metrics demand the configured bearer token`() = testApplication {
        val token = "scrape-token-that-is-long-enough"
        useTestConfig(database, extraConfig = mapOf("observability.metricsToken" to token))

        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.System.METRICS).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get(ApiRoutes.System.METRICS) { bearerAuth("wrong-token-that-is-long-enough") }.status,
        )

        client.get(ApiRoutes.System.HEALTH)
        val scrape = client.get(ApiRoutes.System.METRICS) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, scrape.status)
        assertTrue("ktor_http_server_requests" in scrape.bodyAsText())
    }

    @Test
    fun `readiness checks the database`() = testApplication {
        useTestConfig(database)
        val ready = client.get(ApiRoutes.System.READY)
        assertEquals(HttpStatusCode.OK, ready.status)
        assertTrue("\"database\":\"UP\"" in ready.bodyAsText().replace(" ", ""))
    }

    @Test
    fun `invite tokens are masked in logged paths`() {
        assertEquals("/i/***", redactPath("/i/abc123"))
        assertEquals("${ApiRoutes.Invites.BASE}/***/accept", redactPath(ApiRoutes.Invites.accept("abc123")))
        assertEquals("/api/v1/groups/42", redactPath("/api/v1/groups/42"))
    }

    @Test
    fun `error reports lose emails, tokens and request data`() {
        val event = SentryEvent().apply {
            user = User().apply { email = "ada@example.com"; ipAddress = "10.0.0.1"; id = "user-1" }
            request = Request().apply { headers = mapOf("Authorization" to "Bearer secret") }
            message = Message().apply { formatted = "Key (email)=(ada@example.com) already exists" }
            exceptions = listOf(SentryException().apply { value = "token eyJhbGciOi.eyJzdWIi.c2ln rejected; Bearer abc.def" })
        }

        val scrubbed = PiiScrubber.scrub(event)

        assertNull(scrubbed.user?.email)
        assertNull(scrubbed.user?.ipAddress)
        assertEquals("user-1", scrubbed.user?.id)
        assertNull(scrubbed.request)
        assertEquals("Key (email)=([email]) already exists", scrubbed.message?.formatted)
        val value = scrubbed.exceptions!!.single().value!!
        assertFalse("eyJ" in value, value)
        assertFalse("abc.def" in value, value)
    }
}
