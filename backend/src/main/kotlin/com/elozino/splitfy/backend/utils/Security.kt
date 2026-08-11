package com.elozino.splitfy.backend.utils

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** The authenticated caller, attached to the call by the JWT provider. */
data class UserPrincipal(
    val userId: UUID,
    val email: String,
)

/** Reads the principal or fails with 401 — routes never handle a null caller. */
fun ApplicationCall.requireUser(): UserPrincipal =
    principal<UserPrincipal>() ?: throw AppException.Unauthorized()

/**
 * Password hashing.
 *
 * BCrypt with a configurable cost: the work factor can be raised over time without
 * invalidating existing hashes, because the cost is encoded in the hash itself.
 */
class PasswordHasher(private val cost: Int) {

    fun hash(rawPassword: String): String =
        BCrypt.withDefaults().hashToString(cost, rawPassword.toCharArray())

    fun verify(rawPassword: String, hash: String): Boolean =
        BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified
}

/**
 * Opaque, high-entropy tokens for refresh and OTP flows.
 *
 * Only the SHA-256 digest is persisted. SHA-256 (not BCrypt) is correct here: the
 * inputs are already 256 bits of randomness, so there is nothing to brute-force and
 * verification stays constant-time and cheap.
 */
object TokenGenerator {

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun opaqueToken(byteLength: Int = 32): String =
        encoder.encodeToString(ByteArray(byteLength).also(random::nextBytes))

    /** Numeric one-time code, zero-padded, for email/SMS verification. */
    fun numericOtp(digits: Int = 6): String {
        val bound = generateSequence(1) { it * 10 }.elementAt(digits)
        return random.nextInt(bound).toString().padStart(digits, '0')
    }

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** Guards against timing attacks when comparing secrets of equal length. */
fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

fun AppConfig.SecurityConfig.hasher(): PasswordHasher = PasswordHasher(bcryptCost)
