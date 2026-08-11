package com.elozino.splitify.backend.services

import com.elozino.splitify.api.dto.UpdateProfileRequest
import com.elozino.splitify.backend.repositories.UserRepository
import com.elozino.splitify.backend.utils.AppException
import com.elozino.splitify.domain.model.User
import com.elozino.splitify.validation.Validators
import java.util.UUID

class UserService(private val users: UserRepository) {

    suspend fun me(callerId: UUID): User =
        users.findById(callerId) ?: throw AppException.NotFound("User")

    /** Public profile: the same shape as [me] minus anything private. */
    suspend fun publicProfile(userId: UUID): User =
        users.findById(userId) ?: throw AppException.NotFound("User")

    suspend fun updateProfile(callerId: UUID, request: UpdateProfileRequest): User {
        val errors = buildList {
            request.displayName?.let { name -> Validators.displayName(name)?.let(::add) }
            Validators.phoneNumber(request.phoneNumber)?.let(::add)
            request.defaultCurrency?.let { code ->
                Validators.currency(code, "defaultCurrency")?.let(::add)
            }
        }
        if (errors.isNotEmpty()) throw AppException.Validation(errors)

        return users.updateProfile(
            id = callerId,
            displayName = request.displayName,
            phoneNumber = request.phoneNumber,
            avatarUrl = request.avatarUrl,
            defaultCurrency = request.defaultCurrency,
        ) ?: throw AppException.NotFound("User")
    }

    /**
     * Contact search for the "add people" picker.
     *
     * Requires a minimum query length so the endpoint cannot be walked to enumerate
     * the whole user table.
     */
    suspend fun search(query: String, limit: Int): List<User> {
        if (query.trim().length < MIN_SEARCH_LENGTH) {
            throw AppException.BadRequest("Enter at least $MIN_SEARCH_LENGTH characters to search")
        }
        return users.search(query, limit)
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 3
    }
}
