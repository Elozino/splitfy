package com.elozino.splitfy.api.dto

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** Start changing the account's email; a code goes to [newEmail], not the current address. */
@Serializable
data class EmailChangeRequest(
    val newEmail: String,
)

/** A change waiting for the code sent to [newEmail]. */
@Serializable
data class EmailChangePending(
    val newEmail: String,
    val expiresAt: Instant,
)

@Serializable
data class EmailChangeConfirmRequest(
    val code: String,
)

/** One Google or Apple identity that can sign into the account. */
@Serializable
data class LinkedIdentity(
    val provider: OAuthProvider,
    /** The address the provider reported when it was linked, if it shared one. */
    val email: String? = null,
    val linkedAt: Instant,
)

/**
 * The sign-in methods on the account.
 *
 * [hasPassword] is false for accounts created through Google or Apple that never set
 * a password; such an account cannot unlink its last identity. [availableProviders]
 * are the providers this server can verify tokens for.
 */
@Serializable
data class LinkedAccountsResponse(
    val identities: List<LinkedIdentity>,
    val hasPassword: Boolean,
    val availableProviders: List<OAuthProvider>,
)
