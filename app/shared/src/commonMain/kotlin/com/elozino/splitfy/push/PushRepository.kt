package com.elozino.splitfy.push

import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.DevicePlatform
import com.elozino.splitfy.api.dto.NotificationPreferences
import com.elozino.splitfy.api.dto.RegisterDeviceRequest
import com.elozino.splitfy.api.dto.UnregisterDeviceRequest
import com.elozino.splitfy.api.dto.UpdateNotificationPreferencesRequest
import com.elozino.splitfy.network.ApiCaller
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface PushRepository {
    suspend fun register(token: String, platform: DevicePlatform): DataResult<Unit>
    suspend fun unregister(token: String): DataResult<Unit>
    suspend fun preferences(): DataResult<NotificationPreferences>
    suspend fun updatePreferences(request: UpdateNotificationPreferencesRequest): DataResult<NotificationPreferences>
}

class PushRepositoryImpl(
    private val client: HttpClient,
    private val caller: ApiCaller,
) : PushRepository {

    override suspend fun register(token: String, platform: DevicePlatform): DataResult<Unit> =
        caller.execute { client.post(ApiRoutes.Devices.BASE) { setBody(RegisterDeviceRequest(token, platform)) } }

    override suspend fun unregister(token: String): DataResult<Unit> =
        caller.execute { client.post(ApiRoutes.Devices.UNREGISTER) { setBody(UnregisterDeviceRequest(token)) } }

    override suspend fun preferences(): DataResult<NotificationPreferences> =
        caller.execute { client.get(ApiRoutes.Notifications.PREFERENCES) }

    override suspend fun updatePreferences(
        request: UpdateNotificationPreferencesRequest,
    ): DataResult<NotificationPreferences> =
        caller.execute { client.patch(ApiRoutes.Notifications.PREFERENCES) { setBody(request) } }
}
