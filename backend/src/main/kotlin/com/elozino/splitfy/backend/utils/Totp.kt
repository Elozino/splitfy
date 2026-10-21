package com.elozino.splitfy.backend.utils

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import java.security.MessageDigest

/**
 * Time-based one-time passwords, RFC 6238 (on top of HOTP, RFC 4226).
 *
 * Fixed to what every authenticator app supports without asking: HMAC-SHA1, six
 * digits, 30-second steps. The algorithm and digit count are parameters only so the
 * RFC's own test vectors (which use eight digits and SHA-256/512 too) can be checked.
 */
object Totp {

    const val PERIOD_SECONDS = 30L
    const val DIGITS = 6

    /** One step either side, so a phone clock that is up to ~30 s off still works. */
    const val ALLOWED_DRIFT_STEPS = 1

    fun step(epochSeconds: Long): Long = Math.floorDiv(epochSeconds, PERIOD_SECONDS)

    fun code(
        secret: ByteArray,
        step: Long,
        digits: Int = DIGITS,
        algorithm: String = "HmacSHA1",
    ): String {
        val mac = Mac.getInstance(algorithm).apply { init(SecretKeySpec(secret, algorithm)) }
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
        // Dynamic truncation (RFC 4226 §5.3).
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val modulus = generateSequence(1L) { it * 10 }.elementAt(digits)
        return (binary % modulus).toString().padStart(digits, '0')
    }

    /**
     * The step [code] belongs to, if it is valid within the drift window around
     * [epochSeconds] and later than [afterStep] (so a code is never accepted twice).
     */
    fun matchingStep(secret: ByteArray, code: String, epochSeconds: Long, afterStep: Long = Long.MIN_VALUE): Long? {
        if (code.length != DIGITS || !code.all(Char::isDigit)) return null
        val now = step(epochSeconds)
        return (-ALLOWED_DRIFT_STEPS..ALLOWED_DRIFT_STEPS)
            .map { now + it }
            .filter { it > afterStep }
            .firstOrNull { candidate -> constantTimeEquals(code(secret, candidate), code) }
    }

    /** 160 bits, the size RFC 4226 recommends for SHA-1. */
    fun newSecret(): ByteArray = ByteArray(20).also(SecureRandom()::nextBytes)

    /** What authenticator apps scan. Issuer appears both as a prefix and a parameter, as Google's spec asks. */
    fun otpauthUri(issuer: String, account: String, secret: ByteArray): String {
        val label = "${issuer.urlEncode()}:${account.urlEncode()}"
        return "otpauth://totp/$label?secret=${Base32.encode(secret)}&issuer=${issuer.urlEncode()}" +
            "&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
    }

    private fun String.urlEncode(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
}

/** RFC 4648 base32 without padding: the alphabet authenticator apps expect secrets in. */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String = buildString {
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                append(ALPHABET[(buffer shr (bits - 5)) and 0x1f])
                bits -= 5
            }
        }
        if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 0x1f])
    }

    fun decode(text: String): ByteArray {
        val clean = text.trimEnd('=').replace(" ", "").uppercase()
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (char in clean) {
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "Not a base32 character: $char" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}

/**
 * AES-256-GCM for small secrets that must be read back (TOTP seeds).
 *
 * Output is `base64(iv ‖ ciphertext+tag)`. A database dump alone is not enough to
 * mint codes; the key lives in configuration, not in the database.
 */
class SecretCipher(keyMaterial: String) {

    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(keyMaterial.toByteArray()), "AES")
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain))
    }

    fun decrypt(encoded: String): ByteArray {
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
