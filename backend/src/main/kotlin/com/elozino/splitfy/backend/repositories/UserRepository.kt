package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.backend.schema.dbNow
import com.elozino.splitfy.backend.schema.UsersTable
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toJava
import com.elozino.splitfy.backend.schema.toKotlin
import com.elozino.splitfy.backend.storage.ObjectKeys
import com.elozino.splitfy.domain.model.CurrencyCode
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import org.jetbrains.exposed.sql.*
import java.util.*
import kotlin.time.Clock

/** A user row including the credential hash, which never leaves the data layer. */
data class UserRecord(
    val user: User,
    val passwordHash: String,
    /** False while [passwordHash] is a random value nobody knows (Google/Apple sign-up). */
    val hasPassword: Boolean = true,
)

/**
 * [avatarUrl] turns a stored profile-photo key into a displayable URL, or null when
 * it can't be served.
 */
class UserRepository(private val avatarDisplayUrl: (String) -> String? = { null }) {

    suspend fun create(
        email: String,
        passwordHash: String,
        displayName: String,
        phoneNumber: String?,
        defaultCurrency: CurrencyCode,
        /** True when a provider (Google, Apple) has already proven the address. */
        emailVerified: Boolean = false,
        /** False when [passwordHash] is random and nobody knows the password. */
        hasPassword: Boolean = true,
    ): User = dbQuery {
        val now = dbNow()
        val id = UUID.randomUUID()
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.email] = email.normalizedEmail()
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.displayName] = displayName.trim()
            it[UsersTable.phoneNumber] = phoneNumber
            it[UsersTable.defaultCurrency] = defaultCurrency.code
            if (emailVerified) it[emailVerifiedAt] = now.toJava()
            it[UsersTable.hasPassword] = hasPassword
            it[createdAt] = now.toJava()
            it[updatedAt] = now.toJava()
        }
        User(
            id = UserId(id.toString()),
            email = email.normalizedEmail(),
            displayName = displayName.trim(),
            phoneNumber = phoneNumber,
            defaultCurrency = defaultCurrency,
            createdAt = now,
            emailVerified = emailVerified,
        )
    }

    suspend fun findByEmail(email: String): UserRecord? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.email eq email.normalizedEmail() }
            .singleOrNull()
            ?.toUserRecord(avatarDisplayUrl)
    }

    suspend fun findById(id: UUID): User? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.toUser(avatarDisplayUrl)
    }

    suspend fun findRecordById(id: UUID): UserRecord? = dbQuery {
        UsersTable.selectAll()
            .where { (UsersTable.id eq id) and UsersTable.deletedAt.isNull() }
            .singleOrNull()
            ?.toUserRecord()
    }

    suspend fun findAllByIds(ids: Collection<UUID>): List<User> {
        if (ids.isEmpty()) return emptyList()
        return dbQuery {
            UsersTable.selectAll()
                .where { UsersTable.id inList ids.toSet() }
                .map { it.toUser(avatarDisplayUrl) }
        }
    }

    suspend fun existsByEmail(email: String): Boolean = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.email eq email.normalizedEmail() }
            .limit(1)
            .any()
    }

    /** Name/email prefix search used by the "add people" picker. Never leaks hashes. */
    /**
     * Directory lookup: one verified account whose email matches exactly. Partial or
     * name matching would let any user enumerate strangers' email addresses.
     */
    suspend fun findVerifiedByEmail(email: String): User? = dbQuery {
        UsersTable.selectAll()
            .where { (UsersTable.email eq email.normalizedEmail()) and UsersTable.emailVerifiedAt.isNotNull() }
            .singleOrNull()
            ?.toUser(avatarDisplayUrl)
    }

    suspend fun markEmailVerified(id: UUID): Unit = dbQuery {
        UsersTable.update({ (UsersTable.id eq id) and UsersTable.emailVerifiedAt.isNull() }) {
            it[emailVerifiedAt] = Clock.System.now().toJava()
        }
    }

    suspend fun updateProfile(
        id: UUID,
        displayName: String?,
        phoneNumber: String?,
        avatarUrl: String?,
        defaultCurrency: CurrencyCode?,
    ): User? = dbQuery {
        UsersTable.update({ UsersTable.id eq id }) { statement ->
            displayName?.let { statement[UsersTable.displayName] = it.trim() }
            phoneNumber?.let { statement[UsersTable.phoneNumber] = it }
            // An empty string removes the photo.
            avatarUrl?.let { statement[UsersTable.avatarUrl] = it.ifEmpty { null } }
            defaultCurrency?.let { statement[UsersTable.defaultCurrency] = it.code }
            statement[updatedAt] = Clock.System.now().toJava()
        }
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUser(avatarDisplayUrl)
    }

    /**
     * [usable] is false when [passwordHash] hashes a random value nobody knows, so the
     * account has no password its owner could sign in with.
     */
    suspend fun updatePasswordHash(id: UUID, passwordHash: String, usable: Boolean = true): Boolean = dbQuery {
        UsersTable.update({ UsersTable.id eq id }) {
            it[UsersTable.passwordHash] = passwordHash
            it[hasPassword] = usable
            it[updatedAt] = Clock.System.now().toJava()
        } > 0
    }

    suspend fun hasPassword(id: UUID): Boolean = dbQuery {
        UsersTable.select(UsersTable.hasPassword).where { UsersTable.id eq id }.singleOrNull()?.get(UsersTable.hasPassword)
            ?: false
    }

    /** Moves the account to [email], which the caller has just proven the user owns. */
    suspend fun changeEmail(id: UUID, email: String): Unit = dbQuery {
        val now = Clock.System.now().toJava()
        UsersTable.update({ UsersTable.id eq id }) {
            it[UsersTable.email] = email.normalizedEmail()
            it[emailVerifiedAt] = now
            it[updatedAt] = now
        }
    }

    /**
     * Takes an unproven address away from the account squatting on it: the email
     * becomes a placeholder that can never receive mail, and nobody can sign in with
     * the old password any more. Returns false if the account verified the address in
     * the meantime, in which case it keeps it.
     */
    suspend fun releaseUnverifiedEmail(id: UUID, unusablePasswordHash: String): Boolean = dbQuery {
        UsersTable.update({ (UsersTable.id eq id) and UsersTable.emailVerifiedAt.isNull() }) {
            it[email] = "unclaimed-$id@released.invalid"
            it[passwordHash] = unusablePasswordHash
            it[hasPassword] = false
            it[updatedAt] = Clock.System.now().toJava()
        } > 0
    }

    private companion object {
        fun String.normalizedEmail() = trim().lowercase()
    }
}

internal fun ResultRow.toUser(avatarUrl: (String) -> String? = { null }) = User(
    id = UserId(this[UsersTable.id].toString()),
    email = this[UsersTable.email],
    displayName = this[UsersTable.displayName],
    phoneNumber = this[UsersTable.phoneNumber],
    // Only keys this server issued are resolved; anything stored before uploads existed is dropped.
    avatarUrl = this[UsersTable.avatarUrl]?.takeIf(ObjectKeys::isOurs)?.let(avatarUrl),
    defaultCurrency = CurrencyCode(this[UsersTable.defaultCurrency]),
    createdAt = this[UsersTable.createdAt].toKotlin(),
    emailVerified = this[UsersTable.emailVerifiedAt] != null,
)

internal fun ResultRow.toUserRecord(avatarUrl: (String) -> String? = { null }) = UserRecord(
    user = toUser(avatarUrl),
    passwordHash = this[UsersTable.passwordHash],
    hasPassword = this[UsersTable.hasPassword],
)
