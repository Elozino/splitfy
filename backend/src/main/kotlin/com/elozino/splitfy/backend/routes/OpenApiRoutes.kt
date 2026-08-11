package com.elozino.splitfy.backend.routes

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import com.elozino.splitfy.api.ApiRoutes
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.Route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val SPEC_RESOURCE = "openapi/documentation.yaml"

/** Only here to borrow its class loader; the spec is read from the JAR, not the filesystem. */
private object SpecResource

/**
 * The API description itself, in both representations.
 *
 * Swagger UI already serves the YAML from somewhere under its own mount, but that
 * path belongs to the plugin and is free to change. Client generators, Postman and
 * `openapi-diff` want a stable URL, and most of them want JSON, so both get one.
 *
 * These stay outside `authenticate {}` for the same reason the UI does: a client
 * that cannot read the description cannot work out how to authenticate.
 *
 * The conversion is done here rather than by a Ktor plugin because Ktor does not
 * offer one: `swaggerUI` and `openAPI` serve the spec file's bytes verbatim, and
 * Ktor's own `OpenApiDoc` model only decodes from JSON — its element adapter bails
 * out on any decoder that is not a `JsonDecoder`, so it cannot read this YAML.
 * kaml is nonetheless the same parser Ktor uses for OpenAPI YAML internally.
 */
fun Route.openApiRoutes() {
    // The spec ships inside the JAR and cannot change while the process runs, so
    // read and convert once rather than on every request.
    val yaml by lazy { loadSpec() }
    val json by lazy { specAsJson(yaml) }

    get(ApiRoutes.System.OPENAPI_YAML) {
        call.respondText(yaml, ContentType.Application.Yaml)
    }

    get(ApiRoutes.System.OPENAPI_JSON) {
        call.respondText(json, ContentType.Application.Json)
    }
}

private fun loadSpec(): String =
    checkNotNull(SpecResource::class.java.classLoader.getResourceAsStream(SPEC_RESOURCE)) {
        "$SPEC_RESOURCE is missing from the classpath"
    }.bufferedReader().use { it.readText() }

internal fun specAsJson(yaml: String): String =
    Yaml.default.parseToYamlNode(yaml).toJson().toString()

/**
 * kaml keeps every scalar as text, so the JSON form has to recover the types the
 * YAML implied. It matters: a generator handed `"nullable": "true"` or
 * `"maximum": "100"` will either reject the document or emit string-typed fields.
 *
 * Mapping keys are always emitted as strings, which is what keeps the `"200":`
 * response codes intact rather than turning them into numbers.
 */
private fun YamlNode.toJson(): JsonElement = when (this) {
    is YamlNull -> JsonNull
    is YamlScalar -> toJsonPrimitive()
    is YamlList -> JsonArray(items.map { it.toJson() })
    is YamlMap -> JsonObject(entries.entries.associate { (key, value) -> key.content to value.toJson() })
    is YamlTaggedNode -> innerNode.toJson()
}

private fun YamlScalar.toJsonPrimitive(): JsonPrimitive = when (content) {
    "true" -> JsonPrimitive(true)
    "false" -> JsonPrimitive(false)
    // `1.0.0` and `int64` parse as neither, and fall through to a string.
    else -> content.toLongOrNull()?.let(::JsonPrimitive)
        ?: content.toDoubleOrNull()?.let(::JsonPrimitive)
        ?: JsonPrimitive(content)
}
