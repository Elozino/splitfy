package com.elozino.splitfy.api.dto

import com.elozino.splitfy.domain.model.Balance
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Group
import com.elozino.splitfy.domain.model.GroupCategory
import com.elozino.splitfy.domain.model.GroupRole
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.SuggestedTransfer
import com.elozino.splitfy.domain.model.UserId
import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val category: GroupCategory = GroupCategory.OTHER,
    val currency: CurrencyCode,
    val coverImageUrl: String? = null,
    /** Existing users to add on creation; the creator is always included as OWNER. */
    val memberIds: List<UserId> = emptyList(),
)

@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null,
    val category: GroupCategory? = null,
    val coverImageUrl: String? = null,
)

@Serializable
data class AddMemberRequest(
    val userId: UserId? = null,
    val email: String? = null,
    val role: GroupRole = GroupRole.MEMBER,
)

/** A group plus the caller-specific numbers the group list needs to render. */
@Serializable
data class GroupSummary(
    val group: Group,
    val totalSpend: Money,
    val yourBalance: Money,
    val expenseCount: Int,
)

@Serializable
data class GroupBalancesResponse(
    val groupId: String,
    val currency: CurrencyCode,
    val balances: List<Balance>,
    val suggestedTransfers: List<SuggestedTransfer>,
)
