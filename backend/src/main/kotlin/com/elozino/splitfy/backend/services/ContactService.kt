package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.dto.Contact
import com.elozino.splitfy.api.dto.ContactsResponse
import com.elozino.splitfy.backend.repositories.ExpenseRepository
import com.elozino.splitfy.backend.repositories.GroupRepository
import com.elozino.splitfy.backend.repositories.SettlementRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.schema.ContactFavoritesTable
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.Money
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.domain.split.BalanceCalculator
import java.util.UUID
import kotlin.time.Clock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ContactFavoriteRepository {

    suspend fun favorites(userId: UUID): Set<UUID> = dbQuery {
        ContactFavoritesTable.selectAll()
            .where { ContactFavoritesTable.userId eq userId }
            .mapTo(mutableSetOf()) { it[ContactFavoritesTable.contactUserId] }
    }

    suspend fun add(userId: UUID, contactId: UUID) = dbQuery {
        val exists = ContactFavoritesTable.selectAll()
            .where {
                (ContactFavoritesTable.userId eq userId) and
                    (ContactFavoritesTable.contactUserId eq contactId)
            }
            .any()
        if (!exists) {
            ContactFavoritesTable.insert {
                it[ContactFavoritesTable.userId] = userId
                it[contactUserId] = contactId
                it[createdAt] = Clock.System.now().toJava()
            }
        }
        Unit
    }

    suspend fun remove(userId: UUID, contactId: UUID) = dbQuery {
        ContactFavoritesTable.deleteWhere {
            (ContactFavoritesTable.userId eq userId) and (ContactFavoritesTable.contactUserId eq contactId)
        }
        Unit
    }
}

/**
 * A user's contacts are the people they already share a group with — there is no
 * separate address book to keep in sync, and no reason to ask for phone-book access.
 */
class ContactService(
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val settlements: SettlementRepository,
    private val users: UserRepository,
    private val favorites: ContactFavoriteRepository,
) {

    suspend fun contacts(callerId: UUID, query: String?): ContactsResponse {
        val me = UserId(callerId.toString())
        val currency = users.findById(callerId)?.defaultCurrency ?: CurrencyCode.NGN
        val favoriteIds = favorites.favorites(callerId)

        val sharedGroups = groups.listForUser(callerId, includeArchived = false)
        val sharedCount = mutableMapOf<UserId, Int>()
        val netByUser = mutableMapOf<UserId, Long>()
        val profiles = mutableMapOf<UserId, ContactProfile>()

        sharedGroups.forEach { group ->
            val groupUuid = UUID.fromString(group.id.value)
            val balances = BalanceCalculator.balances(
                currency = group.currency,
                expenses = expenses.allForGroup(groupUuid),
                settlements = settlements.listForGroup(groupUuid),
            )
            val transfers = BalanceCalculator.simplify(balances)

            group.members.filter { it.userId != me }.forEach { member ->
                sharedCount[member.userId] = (sharedCount[member.userId] ?: 0) + 1
                profiles.getOrPut(member.userId) {
                    ContactProfile(member.displayName, member.avatarUrl)
                }
            }

            // Only same-currency groups roll into the headline balance; mixing
            // currencies without an FX rate would be a made-up number.
            if (group.currency == currency) {
                transfers.forEach { transfer ->
                    when (me) {
                        transfer.from -> netByUser[transfer.to] =
                            (netByUser[transfer.to] ?: 0L) - transfer.amount.amount
                        transfer.to -> netByUser[transfer.from] =
                            (netByUser[transfer.from] ?: 0L) + transfer.amount.amount
                        else -> Unit
                    }
                }
            }
        }

        val emails = users
            .findAllByIds(profiles.keys.map { UUID.fromString(it.value) })
            .associate { it.id to it }

        val all = profiles.map { (userId, profile) ->
            val user = emails[userId]
            Contact(
                userId = userId,
                displayName = profile.displayName,
                email = user?.email.orEmpty(),
                avatarUrl = profile.avatarUrl ?: user?.avatarUrl,
                phoneNumber = user?.phoneNumber,
                isFavorite = UUID.fromString(userId.value) in favoriteIds,
                sharedGroupCount = sharedCount[userId] ?: 0,
                netBalance = netByUser[userId]?.let { Money(it, currency) },
            )
        }
            .filter { contact -> query.isNullOrBlank() || contact.matches(query) }
            .sortedBy { it.displayName.lowercase() }

        return ContactsResponse(
            contacts = all,
            favorites = all.filter { it.isFavorite },
        )
    }

    suspend fun setFavorite(callerId: UUID, contactId: UUID, favorite: Boolean): ContactsResponse {
        users.findById(contactId) ?: throw AppException.NotFound("Contact")
        if (favorite) favorites.add(callerId, contactId) else favorites.remove(callerId, contactId)
        return contacts(callerId, query = null)
    }

    private data class ContactProfile(val displayName: String, val avatarUrl: String?)

    private fun Contact.matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        return displayName.lowercase().contains(needle) || email.lowercase().contains(needle)
    }
}
