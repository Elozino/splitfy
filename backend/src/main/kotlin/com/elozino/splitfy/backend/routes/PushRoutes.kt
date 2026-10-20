package com.elozino.splitfy.backend.routes

import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.RegisterDeviceRequest
import com.elozino.splitfy.api.dto.UnregisterDeviceRequest
import com.elozino.splitfy.api.dto.UpdateNotificationPreferencesRequest
import com.elozino.splitfy.backend.services.NotificationService
import com.elozino.splitfy.backend.utils.requireUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post

/** Device registration and push preferences. Both registration calls are idempotent. */
fun Route.pushRoutes(service: NotificationService) {

    post(ApiRoutes.Devices.BASE) {
        service.registerDevice(call.requireUser().userId, call.receive<RegisterDeviceRequest>())
        call.respond(HttpStatusCode.NoContent)
    }

    post(ApiRoutes.Devices.UNREGISTER) {
        service.unregisterDevice(call.requireUser().userId, call.receive<UnregisterDeviceRequest>().token)
        call.respond(HttpStatusCode.NoContent)
    }

    get(ApiRoutes.Notifications.PREFERENCES) {
        call.respond(HttpStatusCode.OK, service.preferences(call.requireUser().userId))
    }

    patch(ApiRoutes.Notifications.PREFERENCES) {
        val updated = service.updatePreferences(
            call.requireUser().userId,
            call.receive<UpdateNotificationPreferencesRequest>(),
        )
        call.respond(HttpStatusCode.OK, updated)
    }
}
