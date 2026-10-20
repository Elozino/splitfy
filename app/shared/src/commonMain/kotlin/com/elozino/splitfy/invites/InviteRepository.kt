package com.elozino.splitfy.invites

import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.ApiRoutes
import com.elozino.splitfy.api.dto.GroupInvite
import com.elozino.splitfy.api.dto.InvitePreview
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.network.ApiCaller
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post

class InviteRepository(private val client: HttpClient, private val caller: ApiCaller) {
    suspend fun current(groupId: String): DataResult<GroupInvite> =
        caller.execute { client.get(ApiRoutes.Groups.invite(groupId)) }
    suspend fun reset(groupId: String): DataResult<GroupInvite> =
        caller.execute { client.post(ApiRoutes.Groups.resetInvite(groupId)) }
    suspend fun preview(token: String): DataResult<InvitePreview> =
        caller.execute { client.get(ApiRoutes.Invites.byToken(token)) }
    suspend fun accept(token: String): DataResult<Group> =
        caller.execute { client.post(ApiRoutes.Invites.accept(token)) }
}
