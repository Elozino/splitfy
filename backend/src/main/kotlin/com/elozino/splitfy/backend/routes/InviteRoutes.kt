package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.backend.services.InviteService
import com.elozino.splitfy.backend.utils.AppConfig
import com.elozino.splitfy.backend.utils.requireUser
import com.elozino.splitfy.backend.utils.uuidParam
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Behind authentication: sharing, resetting and accepting links. */
fun Route.inviteRoutes(service: InviteService) {

    get(ApiRoutes.Groups.invite("{groupId}")) {
        call.respond(HttpStatusCode.OK, service.current(call.uuidParam("groupId"), call.requireUser().userId))
    }

    post(ApiRoutes.Groups.resetInvite("{groupId}")) {
        call.respond(HttpStatusCode.OK, service.reset(call.uuidParam("groupId"), call.requireUser().userId))
    }

    post(ApiRoutes.Invites.accept("{token}")) {
        call.respond(HttpStatusCode.OK, service.accept(call.parameters["token"].orEmpty(), call.requireUser().userId))
    }
}

/** Public: the preview, the page a link opens without the app, and app-link verification. */
fun Route.publicInviteRoutes(service: InviteService, links: AppConfig.LinksConfig) {

    get(ApiRoutes.Invites.byToken("{token}")) {
        call.respond(HttpStatusCode.OK, service.preview(call.parameters["token"].orEmpty()))
    }

    // Opened in a browser when the app isn't installed (or links aren't verified yet).
    get("/i/{token}") {
        val token = call.parameters["token"].orEmpty()
        val preview = runCatching { service.preview(token) }.getOrNull()
        call.respondText(invitePage(preview?.groupName, preview?.invitedBy, token), ContentType.Text.Html)
    }

    // Lets Android open https links on this host directly in the app.
    get("/.well-known/assetlinks.json") {
        val body = buildJsonArray {
            addJsonObject {
                putJsonArray("relation") { add("delegate_permission/common.handle_all_urls") }
                putJsonObject("target") {
                    put("namespace", "android_app")
                    put("package_name", links.androidPackage)
                    putJsonArray("sha256_cert_fingerprints") { links.androidCertSha256.forEach { add(it) } }
                }
            }
        }
        call.respondText(Json.encodeToString(body), ContentType.Application.Json)
    }

    // The iOS counterpart: which app ids may open /i/* links.
    get("/.well-known/apple-app-site-association") {
        val body = buildJsonObject {
            putJsonObject("applinks") {
                putJsonArray("details") {
                    addJsonObject {
                        putJsonArray("appIDs") { links.appleAppIds.forEach { add(it) } }
                        putJsonArray("components") { addJsonObject { put("/", "/i/*") } }
                    }
                }
            }
        }
        call.respondText(Json.encodeToString(body), ContentType.Application.Json)
    }
}

private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun invitePage(groupName: String?, invitedBy: String?, token: String): String {
    val heading = if (groupName != null) {
        "${invitedBy.orEmpty().escapeHtml()} invited you to <b>${groupName.escapeHtml()}</b>"
    } else {
        "This invite link has expired or was reset"
    }
    val appLink = "splitfy://invite/${token.escapeHtml()}"
    return """
        <!doctype html>
        <html lang="en"><head>
          <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
          <title>Join on splitfy</title>
          <style>
            body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;margin:0;padding:48px 24px;text-align:center;color:#111;background:#fff}
            h1{font-size:22px;font-weight:600;margin:0 0 12px}p{color:#555;margin:0 0 28px}
            a.btn{display:inline-block;background:#FEBB1B;color:#111;padding:14px 28px;border-radius:999px;text-decoration:none;font-weight:600}
          </style>
        </head><body>
          <h1>$heading</h1>
          <p>splitfy keeps track of who owes what in a group.</p>
          ${if (groupName != null) """<a class="btn" href="$appLink">Open in splitfy</a>""" else ""}
        </body></html>
    """.trimIndent()
}
