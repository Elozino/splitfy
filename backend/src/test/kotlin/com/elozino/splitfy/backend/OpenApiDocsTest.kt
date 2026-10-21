package com.elozino.splitfy.backend

import com.elozino.splitfy.api.ApiRoutes
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Guards the API documentation.
 *
 * The spec is hand-written rather than generated from the routes, so nothing but a
 * test stops the two drifting apart. `every registered path is documented` is the
 * one that earns its keep: adding a route without describing it fails the build
 * instead of quietly shipping an endpoint no client knows about.
 */
class OpenApiDocsTest {

    private val database = TestDatabase("splitfy_openapi")

    @BeforeTest
    fun setUp() = database.reset()

    @Test
    fun `swagger ui is served without authentication`() = testApplication {
        useTestConfig(database)

        // No bearer token: the docs sit outside the authenticated block on purpose,
        // because a client that cannot read them cannot work out how to sign in.
        val response = client.get(ApiRoutes.System.OPENAPI)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.bodyAsText().contains("swagger", ignoreCase = true),
            "Expected the Swagger UI page at ${ApiRoutes.System.OPENAPI}",
        )
    }

    @Test
    fun `the spec itself is served and parses as the splitfy API`() = testApplication {
        useTestConfig(database)

        val response = client.get("${ApiRoutes.System.OPENAPI}/documentation.yaml")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.startsWith("openapi:"), "Expected an OpenAPI document, got: ${body.take(80)}")
        assertTrue(body.contains("title: splitfy API"))
    }

    @Test
    fun `the spec is served as json for client generators`() = testApplication {
        useTestConfig(database)

        val response = client.get(ApiRoutes.System.OPENAPI_JSON)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val spec = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("3.0.3", spec["openapi"]?.jsonPrimitive?.content)
        assertEquals("splitfy API", spec["info"]!!.jsonObject["title"]?.jsonPrimitive?.content)
        assertEquals(documentedPaths(), spec["paths"]!!.jsonObject.keys)
    }

    @Test
    fun `the json conversion recovers the types the yaml implied`() = testApplication {
        useTestConfig(database)

        val spec = Json.parseToJsonElement(client.get(ApiRoutes.System.OPENAPI_JSON).bodyAsText()).jsonObject
        val schemas = spec["components"]!!.jsonObject["schemas"]!!.jsonObject

        // A generator handed `"nullable": "true"` emits a string-typed field, so the
        // booleans and numbers have to survive the round trip as booleans and numbers.
        val last4 = schemas["PaymentMethod"]!!.jsonObject["properties"]!!.jsonObject["last4"]!!.jsonObject
        assertEquals(true, last4["nullable"]?.jsonPrimitive?.boolean)

        val limit = spec["components"]!!.jsonObject["parameters"]!!.jsonObject["Limit"]!!.jsonObject
        assertEquals(100, limit["schema"]!!.jsonObject["maximum"]?.jsonPrimitive?.int)

        // Response codes are mapping keys and must stay strings, not become numbers.
        val health = spec["paths"]!!.jsonObject["/health"]!!.jsonObject["get"]!!.jsonObject
        assertTrue("200" in health["responses"]!!.jsonObject.keys, "Expected a \"200\" response key")

        // `1.0.0` is not a number; it must not be mangled into one.
        assertEquals("1.0.0", spec["info"]!!.jsonObject["version"]?.jsonPrimitive?.content)
    }

    /**
     * Ktor's own OpenAPI model is the closest thing to a reference parser we can run
     * in-process, so decoding into it is a real structural check rather than a
     * string comparison: a spec that has drifted into something malformed fails here.
     *
     * It can only be pointed at the JSON. `OpenApiDoc` decodes through an adapter that
     * returns null for any decoder that is not a `JsonDecoder`, so handing it the YAML
     * fails with "Cannot deserialize map to ReferenceOr" — which is why the conversion
     * in `OpenApiRoutes` is ours and not a Ktor plugin's.
     */
    @Test
    fun `the served json parses as a valid openapi document`() = testApplication {
        useTestConfig(database)

        val body = client.get(ApiRoutes.System.OPENAPI_JSON).bodyAsText()
        val doc = Json { ignoreUnknownKeys = true }.decodeFromString(OpenApiDoc.serializer(), body)

        assertEquals("splitfy API", doc.info.title)
        assertEquals(documentedPaths(), doc.paths.keys)
        assertEquals(65, doc.components?.schemas?.size)
        assertTrue(doc.tags.orEmpty().isNotEmpty(), "Expected the tag list to survive the conversion")
    }

    @Test
    fun `the spec is served as yaml at a stable path`() = testApplication {
        useTestConfig(database)

        val response = client.get(ApiRoutes.System.OPENAPI_YAML)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().startsWith("openapi:"))
    }

    @Test
    fun `every registered path is documented`() = testApplication {
        useTestConfig(database)
        startApplication()

        val documented = documentedPaths()
        val registered = application.registeredApiPaths()

        // Both sides are derived by scraping, so an empty set would make the
        // comparison below pass without checking anything.
        assertTrue(documented.size > 30, "Only parsed ${documented.size} paths out of the spec")
        assertTrue(registered.size > 30, "Only walked ${registered.size} paths out of the routing tree")

        val undocumented = registered.filterNot { it in documented }

        assertTrue(
            undocumented.isEmpty(),
            "These routes are registered but missing from openapi/documentation.yaml:\n" +
                undocumented.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `every documented path is registered`() = testApplication {
        useTestConfig(database)
        startApplication()

        val registered = application.registeredApiPaths()
        val stale = documentedPaths().filterNot { it in registered }

        assertTrue(
            stale.isEmpty(),
            "These paths are documented but no longer served:\n" + stale.joinToString("\n") { "  $it" },
        )
    }

    /**
     * Every API path the routing tree actually serves, in OpenAPI's `{param}` form.
     *
     * Limited to the documented surface — the Swagger UI's own routes under
     * `/openapi` describe nothing and are deliberately absent from the spec.
     */
    private fun Application.registeredApiPaths(): Set<String> {
        val paths = mutableSetOf<String>()

        fun walk(node: RoutingNode, prefix: String) {
            when (val selector = node.selector) {
                // A method leaf carries no segment of its own: whatever path led here
                // is a real, callable endpoint.
                is HttpMethodRouteSelector -> paths += prefix.ifEmpty { "/" }
                is PathSegmentConstantRouteSelector -> return node.children.forEach {
                    walk(it, "$prefix/${selector.value}")
                }
                is PathSegmentParameterRouteSelector -> return node.children.forEach {
                    walk(it, "$prefix/{${selector.name}}")
                }
                else -> Unit // authenticate {}, rateLimit {}, trailing slash — no segment
            }
            node.children.forEach { walk(it, prefix) }
        }

        walk(plugin(RoutingRoot), "")

        return paths.filterTo(mutableSetOf()) {
            it.startsWith("${ApiRoutes.API_PREFIX}/") ||
                it == ApiRoutes.System.HEALTH ||
                it == ApiRoutes.System.READY ||
                it == ApiRoutes.System.METRICS
        }
    }

    /**
     * Top-level `paths:` keys, read straight from the resource the server serves.
     *
     * A regex rather than a YAML parser keeps the test dependency-free; the keys are
     * the only two-space-indented lines that start with a slash.
     */
    private fun documentedPaths(): Set<String> {
        val yaml = checkNotNull(javaClass.classLoader.getResourceAsStream("openapi/documentation.yaml")) {
            "openapi/documentation.yaml is missing from the classpath"
        }.bufferedReader().readText()

        return Regex("""^ {2}(/\S*):$""", RegexOption.MULTILINE)
            .findAll(yaml.substringAfter("\npaths:").substringBefore("\ncomponents:"))
            .map { it.groupValues[1] }
            .toSet()
    }
}
