package com.elozino.splitfy.backend.payments

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class PaymentKind { TOP_UP, WITHDRAWAL }
enum class PaymentStatus { PENDING, COMPLETED, REVERSED }
data class PaymentIntent(
    val id: UUID, val userId: UUID, val kind: PaymentKind, val amount: Long,
    val currency: String, val reference: String, val destination: String, val status: PaymentStatus,
)
data class ProviderObservation(val reference: String, val amount: Long, val currency: String, val status: String)
internal data class VerifiedEvent(
    val type: String, val observation: ProviderObservation, val customerEmail: String?,
    val authorization: JsonObject?,
)

interface PaymentProvider {
    suspend fun initialize(intent: PaymentIntent): String
    suspend fun withdraw(intent: PaymentIntent)
    suspend fun verify(intent: PaymentIntent): ProviderObservation
}

/** Deliberately rejects live keys. No caller can enable live payments with a feature flag. */
class PaystackSandboxProvider(private val client: HttpClient, private val secret: String) : PaymentProvider {
    init { require(secret.startsWith("sk_test_") && secret.length > 8) { "A Paystack test secret is required" } }
    override suspend fun initialize(intent: PaymentIntent): String {
        require(intent.kind == PaymentKind.TOP_UP)
        val data = request("transaction/initialize", buildJsonObject {
            put("email", intent.destination); put("amount", intent.amount)
            put("currency", intent.currency); put("reference", intent.reference)
        })
        require(data.string("reference") == intent.reference)
        return data.string("authorization_url").also { require(it.startsWith("https://checkout.paystack.com/")) }
    }
    override suspend fun withdraw(intent: PaymentIntent) {
        require(intent.kind == PaymentKind.WITHDRAWAL)
        val data = request("transfer", buildJsonObject {
            put("source", "balance"); put("amount", intent.amount); put("currency", intent.currency)
            put("recipient", intent.destination); put("reference", intent.reference)
        })
        require(data.string("reference") == intent.reference)
        // An HTTP success, including an OTP/pending response, never completes the local intent.
    }
    override suspend fun verify(intent: PaymentIntent): ProviderObservation =
        observation(request(if (intent.kind == PaymentKind.TOP_UP) "transaction/verify/${intent.reference}"
            else "transfer/verify/${intent.reference}"))

    private suspend fun request(path: String, payload: JsonObject? = null): JsonObject {
        val response = client.request("https://api.paystack.co/$path") {
            method = if (payload == null) HttpMethod.Get else HttpMethod.Post
            bearerAuth(secret)
            if (payload != null) { contentType(ContentType.Application.Json); setBody(payload.toString()) }
        }
        check(response.status.isSuccess()) { "Paystack request failed (${response.status.value}); reconcile before retrying" }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        check(body["status"]?.jsonPrimitive?.booleanOrNull == true) { "Paystack rejected request; reconcile before retrying" }
        return body.getValue("data").jsonObject
    }
}

class PaystackWebhookVerifier(private val secret: String) {
    init { require(secret.startsWith("sk_test_") && secret.length > 8) }
    internal fun verify(raw: ByteArray, signature: String?): VerifiedEvent? {
        require(raw.size <= 256 * 1024) { "Webhook too large" }
        require(signature != null && signature.matches(Regex("[0-9a-fA-F]{128}"))) { "Invalid webhook signature" }
        val mac = Mac.getInstance("HmacSHA512").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA512")) }
        val supplied = signature.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(MessageDigest.isEqual(mac.doFinal(raw), supplied)) { "Invalid webhook signature" }
        val body = Json.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
        val type = body.string("event")
        if (type !in setOf("charge.success", "transfer.success", "transfer.failed", "transfer.reversed")) return null
        val data = body.getValue("data").jsonObject
        require(data.string("domain") == "test") { "Only test events are accepted" }
        return VerifiedEvent(type, observation(data), data["customer"]?.jsonObject?.get("email")?.jsonPrimitive?.content,
            data["authorization"] as? JsonObject)
    }
}
internal fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
private fun observation(data: JsonObject) = ProviderObservation(
    data.string("reference"), data.getValue("amount").jsonPrimitive.long, data.string("currency"), data.string("status"),
)
