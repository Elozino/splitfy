package com.elozino.splitfy.backend.services

import com.elozino.splitfy.api.FieldError
import com.elozino.splitfy.api.dto.UpdateProfileRequest
import com.elozino.splitfy.api.dto.UploadPurpose
import com.elozino.splitfy.backend.repositories.UploadRepository
import com.elozino.splitfy.backend.repositories.UserRepository
import com.elozino.splitfy.backend.storage.ObjectKeys
import com.elozino.splitfy.backend.utils.AppException
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.validation.Validators
import java.util.UUID

class UserService(
    private val users: UserRepository,
    private val uploads: UploadRepository,
) {

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

        // An empty string removes the photo; anything else must be an avatar key issued to the caller.
        request.avatarUrl?.takeIf { it.isNotEmpty() }?.let { key ->
            if (!ObjectKeys.belongsToUser(key, callerId) || !uploads.wasIssuedToUser(key, callerId, UploadPurpose.AVATAR)) {
                throw AppException.Validation(listOf(FieldError("avatarUrl", "Upload the photo through splitfy, then attach it")))
            }
        }

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
    /**
     * Finds someone outside your groups by their full email address. Matching on
     * anything less would let any signed-in user list strangers' addresses.
     */
    suspend fun search(query: String, limit: Int): List<User> {
        if (query.trim().length < MIN_SEARCH_LENGTH) {
            throw AppException.BadRequest("Enter at least $MIN_SEARCH_LENGTH characters to search")
        }
        return listOfNotNull(users.findVerifiedByEmail(query)).take(limit)
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 3
    }
}
