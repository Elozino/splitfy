package com.elozino.splitfy.crash

import kotlin.test.Test
import kotlin.test.assertEquals

class PiiScrubberTest {

    @Test
    fun emailsTokensAndQueryStringsAreRemoved() {
        assertEquals(
            "GET https://splitfy.onrender.com/api/v1/users/search?[query] failed for [email]",
            PiiScrubber.scrub("GET https://splitfy.onrender.com/api/v1/users/search?q=ada@example.com failed for ada@example.com"),
        )
        assertEquals("Authorization: Bearer [token]", PiiScrubber.scrub("Authorization: Bearer abc.def-ghi"))
        assertEquals("token [jwt] expired", PiiScrubber.scrub("token eyJhbGci.eyJzdWIi.sig expired"))
    }

    @Test
    fun ordinaryTextIsUntouched() {
        val text = "IllegalStateException: Group 42 has no members"
        assertEquals(text, PiiScrubber.scrub(text))
    }
}
