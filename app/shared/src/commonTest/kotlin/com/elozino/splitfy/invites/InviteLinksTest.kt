package com.elozino.splitfy.invites

import kotlin.test.*

class InviteLinksTest {
    private val token = "abcdefghijklmnopqrstuv"
    @AfterTest fun cleanUp() = InviteLinks.clear()

    @Test fun restoresPendingInviteAndClearsItFromStorage() {
        val settings = com.russhwolf.settings.MapSettings()
        settings.putString("invites.pending_token", token)
        InviteLinks.bind(settings)
        assertEquals(token, InviteLinks.token.value)
        InviteLinks.clear()
        assertNull(settings.getStringOrNull("invites.pending_token"))
        InviteLinks.open("https://splitfy.app/i/$token")
        assertEquals(token, settings.getStringOrNull("invites.pending_token"))
    }

    @Test fun acceptsWebAndAppLinks() {
        assertEquals(token, InviteLinks.parse("https://splitfy.app/i/$token"))
        assertEquals(token, InviteLinks.parse("splitfy://invite/$token"))
        assertEquals(token, InviteLinks.parse("https://splitfy.app/i/$token?source=share"))
    }

    @Test fun rejectsForeignHostsAndMalformedTokens() {
        listOf("https://evil.test/i/$token", "https://splitfy.app.evil.test/i/$token", "http://splitfy.app/i/$token",
            "https://splitfy.app/i/../auth", "splitfy://invite/short", "https://splitfy.app/i/$token/extra")
            .forEach { assertNull(InviteLinks.parse(it), it) }
    }

    @Test fun holdsLinkUntilExplicitlyDismissed() {
        InviteLinks.open("https://splitfy.app/i/$token")
        InviteLinks.open("https://unrelated.test/")
        assertEquals(token, InviteLinks.token.value)
        InviteLinks.clear()
        assertNull(InviteLinks.token.value)
    }
}
