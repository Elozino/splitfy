package com.elozino.splitfy.security

import com.elozino.splitfy.SessionManager
import com.elozino.splitfy.api.dto.TokenPair
import com.elozino.splitfy.domain.model.User
import com.elozino.splitfy.domain.model.UserId
import com.elozino.splitfy.storage.SessionStorage
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

class AppLockControllerTest {

    private val time = TestTimeSource()
    private val storage = MapSettings()

    private fun signedInSession() = SessionManager(SessionStorage(MapSettings())).apply {
        onAuthenticated(
            User(id = UserId("u1"), email = "ada@example.com", displayName = "Ada", createdAt = Instant.fromEpochSeconds(0)),
            TokenPair("access", "refresh", 900),
        )
    }

    private fun controller(enabled: Boolean, timeoutMinutes: Int = 1, session: SessionManager = signedInSession()): AppLockController {
        val settings = AppLockSettings(storage).apply {
            setEnabled(enabled)
            setTimeoutMinutes(timeoutMinutes)
        }
        return AppLockController(settings, session, time)
    }

    @Test
    fun `a cold start is locked only when app lock is on`() {
        assertTrue(controller(enabled = true).locked.value)
        assertFalse(controller(enabled = false).locked.value)
    }

    @Test
    fun `nothing is locked while signed out`() {
        val signedOut = SessionManager(SessionStorage(MapSettings()))
        val lock = controller(enabled = true, session = signedOut)

        assertFalse(lock.locked.value)
        lock.onBackground()
        time += 10.minutes
        lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `returning before the timeout stays unlocked and after it locks`() {
        val lock = controller(enabled = true, timeoutMinutes = 1).apply { unlock() }

        lock.onBackground()
        time += 59.seconds
        lock.onForeground()
        assertFalse(lock.locked.value)

        lock.onBackground()
        time += 1.minutes
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `immediately means every return locks`() {
        val lock = controller(enabled = true, timeoutMinutes = 0).apply { unlock() }

        lock.onBackground()
        lock.onForeground()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `turning app lock off releases a locked app`() {
        val lock = controller(enabled = true)
        assertTrue(lock.locked.value)

        lock.setEnabled(false)

        assertFalse(lock.locked.value)
        lock.onBackground()
        time += 1.minutes
        lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `device names become safe header values`() {
        assertEquals("Ada's iPhone", "Ada’s iPhone".toHeaderValue())
        assertEquals("Pixel", "  Pixel   ".toHeaderValue())
        assertEquals("Caf", "Café".toHeaderValue())
        assertEquals(100, "x".repeat(300).toHeaderValue().length)
    }
}
