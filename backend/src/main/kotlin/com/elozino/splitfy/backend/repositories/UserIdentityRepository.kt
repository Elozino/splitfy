package com.elozino.splitfy.backend.repositories

import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.backend.schema.UserIdentitiesTable
import com.elozino.splitfy.backend.schema.dbQuery
import com.elozino.splitfy.backend.schema.toJava
import java.util.UUID
import kotlin.time.Clock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll

/** Links between an account and the Google/Apple identities that can sign into it. */
class UserIdentityRepository {

    suspend fun findUserId(provider: OAuthProvider, subject: String): UUID? = dbQuery {
        UserIdentitiesTable.selectAll()
            .where { (UserIdentitiesTable.provider eq provider.name) and (UserIdentitiesTable.subject eq subject) }
            .singleOrNull()
            ?.get(UserIdentitiesTable.userId)
    }

    /** Idempotent: linking the same identity twice is a no-op. */
    suspend fun link(provider: OAuthProvider, subject: String, userId: UUID, email: String?): Unit = dbQuery {
        UserIdentitiesTable.insertIgnore {
            it[UserIdentitiesTable.provider] = provider.name
            it[UserIdentitiesTable.subject] = subject
            it[UserIdentitiesTable.userId] = userId
            it[UserIdentitiesTable.email] = email
            it[createdAt] = Clock.System.now().toJava()
        }
    }
}
