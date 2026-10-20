package com.elozino.splitfy.backend

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.elozino.splitfy.backend.services.FcmPushSender
import com.elozino.splitfy.backend.services.FcmServiceAccount
import com.elozino.splitfy.backend.services.LoggingPushSender
import com.elozino.splitfy.backend.services.PushMessage
import com.elozino.splitfy.backend.services.PushResult
import com.elozino.splitfy.backend.services.pushSender
import com.elozino.splitfy.backend.utils.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.serialization.kotlinx.json.json
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FcmPushSenderTest {

    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keys.private.encoded) +
        "\n-----END PRIVATE KEY-----\n"
    private val account = FcmServiceAccount(
        projectId = "splitfy-test",
        clientEmail = "push@splitfy-test.iam.gserviceaccount.com",
        privateKey = pem,
        tokenUri = "https://oauth2.example.test/token",
    )
    private val message = PushMessage("New expense in Flat", "ada added Groceries", mapOf("groupId" to "g1"))

    private class Recorder(
        val fcm: suspend MockRequestHandleScope.(HttpRequestData, Int) -> HttpResponseData,
    ) {
        val tokenRequests = mutableListOf<String>()
        val sends = mutableListOf<Pair<String?, String>>()

        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            if (request.url.host == "oauth2.example.test") {
                tokenRequests += body
                respond(
                    """{"access_token":"access-${tokenRequests.size}","expires_in":3599,"token_type":"Bearer"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                sends += request.headers[HttpHeaders.Authorization] to body
                fcm(request, sends.size)
            }
        }
    }

    private fun sender(recorder: Recorder) = FcmPushSender(
        HttpClient(recorder.engine) { install(ContentNegotiation) { json(Json { explicitNulls = false }) } },
        account,
    )

    @Test
    fun `signs a jwt bearer grant, caches the access token and sends the v1 message`() = runTest {
        val recorder = Recorder { _, _ -> respond("""{"name":"projects/splitfy-test/messages/1"}""", HttpStatusCode.OK) }
        val sender = sender(recorder)

        assertEquals(PushResult.SENT, sender.send("device-1", message))
        assertEquals(PushResult.SENT, sender.send("device-2", message))

        // One token exchange for both sends.
        val grant = recorder.tokenRequests.single().parseUrlEncodedParameters()
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", grant["grant_type"])
        val assertion = JWT.require(Algorithm.RSA256(keys.public as RSAPublicKey, null))
            .withIssuer(account.clientEmail)
            .withAudience(account.tokenUri)
            .build()
            .verify(grant["assertion"])
        assertEquals(FcmPushSender.SCOPE, assertion.getClaim("scope").asString())

        val (auth, body) = recorder.sends.first()
        assertEquals("Bearer access-1", auth)
        val sent = Json.parseToJsonElement(body).jsonObject["message"]!!.jsonObject
        assertEquals("device-1", sent["token"]!!.jsonPrimitive.content)
        assertEquals("New expense in Flat", sent["notification"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals("g1", sent["data"]!!.jsonObject["groupId"]!!.jsonPrimitive.content)
        assertEquals(
            FcmPushSender.ANDROID_CHANNEL_ID,
            sent["android"]!!.jsonObject["notification"]!!.jsonObject["channel_id"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `an unregistered token is reported so it can be deleted`() = runTest {
        val recorder = Recorder { _, _ ->
            respond(
                """{"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",
                   "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}""",
                HttpStatusCode.NotFound,
            )
        }
        assertEquals(PushResult.INVALID_TOKEN, sender(recorder).send("gone", message))
    }

    @Test
    fun `a malformed token is invalid but other bad requests are not`() = runTest {
        val badToken = Recorder { _, _ ->
            respond(
                """{"error":{"code":400,"message":"The registration token is not a valid FCM registration token",
                   "status":"INVALID_ARGUMENT","details":[{"errorCode":"INVALID_ARGUMENT"}]}}""",
                HttpStatusCode.BadRequest,
            )
        }
        assertEquals(PushResult.INVALID_TOKEN, sender(badToken).send("junk", message))

        val serverTrouble = Recorder { _, _ -> respond("""{"error":{"code":503,"status":"UNAVAILABLE"}}""", HttpStatusCode.ServiceUnavailable) }
        assertEquals(PushResult.FAILED, sender(serverTrouble).send("device", message))

        // A bare 404 (wrong project id) must not look like a dead token.
        val wrongProject = Recorder { _, _ -> respond("Not Found", HttpStatusCode.NotFound) }
        assertEquals(PushResult.FAILED, sender(wrongProject).send("device", message))
    }

    @Test
    fun `a rejected access token is refreshed once and the send retried`() = runTest {
        val recorder = Recorder { _, attempt ->
            if (attempt == 1) respond("""{"error":{"code":401}}""", HttpStatusCode.Unauthorized)
            else respond("{}", HttpStatusCode.OK)
        }
        assertEquals(PushResult.SENT, sender(recorder).send("device", message))
        assertEquals(2, recorder.tokenRequests.size)
        assertEquals(listOf("Bearer access-1", "Bearer access-2"), recorder.sends.map { it.first })
    }

    @Test
    fun `an unconfigured or unreadable key falls back to logging instead of failing`() {
        val client = HttpClient(MockEngine { respond("") })
        assertIs<LoggingPushSender>(pushSender(AppConfig.PushConfig(), client))
        assertIs<LoggingPushSender>(pushSender(AppConfig.PushConfig(fcmServiceAccountJson = "{not json"), client))
        assertIs<LoggingPushSender>(pushSender(AppConfig.PushConfig(fcmServiceAccountPath = "/does/not/exist.json"), client))
        val json = Json.encodeToString(FcmServiceAccount.serializer(), account)
        assertIs<FcmPushSender>(pushSender(AppConfig.PushConfig(fcmServiceAccountJson = json), client))
        assertTrue(json.contains("private_key"))
    }
}
