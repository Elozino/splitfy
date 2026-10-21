package com.elozino.splitfy.backend

import com.elozino.splitfy.backend.utils.Base32
import com.elozino.splitfy.backend.utils.SecretCipher
import com.elozino.splitfy.backend.utils.Totp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TotpTest {

    // RFC 6238 Appendix B: the seeds are ASCII "1234567890" repeated to the hash's block size.
    private val sha1Seed = "12345678901234567890".toByteArray()
    private val sha256Seed = "12345678901234567890123456789012".toByteArray()
    private val sha512Seed = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    /** time -> (SHA1, SHA256, SHA512), eight digits each, straight from the RFC's table. */
    private val vectors = mapOf(
        59L to Triple("94287082", "46119246", "90693936"),
        1111111109L to Triple("07081804", "68084774", "25091201"),
        1111111111L to Triple("14050471", "67062674", "99943326"),
        1234567890L to Triple("89005924", "91819424", "93441116"),
        2000000000L to Triple("69279037", "90698825", "38618901"),
        20000000000L to Triple("65353130", "77737706", "47863826"),
    )

    @Test
    fun `matches the RFC 6238 test vectors`() {
        for ((time, expected) in vectors) {
            val step = Totp.step(time)
            assertEquals(expected.first, Totp.code(sha1Seed, step, digits = 8, algorithm = "HmacSHA1"), "SHA1 @ $time")
            assertEquals(expected.second, Totp.code(sha256Seed, step, digits = 8, algorithm = "HmacSHA256"), "SHA256 @ $time")
            assertEquals(expected.third, Totp.code(sha512Seed, step, digits = 8, algorithm = "HmacSHA512"), "SHA512 @ $time")
        }
    }

    @Test
    fun `six-digit codes are the last six digits of the eight-digit ones`() {
        // HOTP truncates modulo 10^digits, so this is what authenticator apps show.
        assertEquals("287082", Totp.code(sha1Seed, Totp.step(59)))
        assertEquals("005924", Totp.code(sha1Seed, Totp.step(1234567890)))
    }

    @Test
    fun `accepts one step of clock drift either way and nothing further`() {
        val now = 1234567890L
        val step = Totp.step(now)
        for (offset in -1..1) {
            val code = Totp.code(sha1Seed, step + offset)
            assertEquals(step + offset, Totp.matchingStep(sha1Seed, code, now))
        }
        assertNull(Totp.matchingStep(sha1Seed, Totp.code(sha1Seed, step + 2), now))
        assertNull(Totp.matchingStep(sha1Seed, Totp.code(sha1Seed, step - 2), now))
    }

    @Test
    fun `a code at or before the last used step is refused`() {
        val now = 1234567890L
        val step = Totp.step(now)
        val code = Totp.code(sha1Seed, step)

        assertNull(Totp.matchingStep(sha1Seed, code, now, afterStep = step))
        assertEquals(step, Totp.matchingStep(sha1Seed, code, now, afterStep = step - 1))
    }

    @Test
    fun `malformed codes never match`() {
        assertNull(Totp.matchingStep(sha1Seed, "12345", 59))
        assertNull(Totp.matchingStep(sha1Seed, "abcdef", 59))
        assertNull(Totp.matchingStep(sha1Seed, "2870821", 59))
    }

    @Test
    fun `base32 matches RFC 4648 and round-trips`() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Base32.encode(sha1Seed))
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
        assertContentEquals("foobar".toByteArray(), Base32.decode("mzxw 6ytb oi======"))
        val secret = Totp.newSecret()
        assertContentEquals(secret, Base32.decode(Base32.encode(secret)))
    }

    @Test
    fun `the otpauth uri carries the secret and issuer`() {
        val uri = Totp.otpauthUri("splitfy", "ada+test@example.com", sha1Seed)
        assertEquals(
            "otpauth://totp/splitfy:ada%2Btest%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                "&issuer=splitfy&algorithm=SHA1&digits=6&period=30",
            uri,
        )
    }

    @Test
    fun `secrets are encrypted with a fresh nonce and only open with the same key`() {
        val cipher = SecretCipher("a-key-that-is-at-least-thirty-two-chars")
        val first = cipher.encrypt(sha1Seed)
        val second = cipher.encrypt(sha1Seed)

        assertTrue(first != second, "Two encryptions of the same secret must differ")
        assertContentEquals(sha1Seed, cipher.decrypt(first))
        assertFailsWith<Exception> { SecretCipher("some-other-key-that-is-long-enough!!").decrypt(first) }
    }
}
