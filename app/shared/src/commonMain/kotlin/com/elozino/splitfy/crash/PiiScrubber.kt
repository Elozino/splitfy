package com.elozino.splitfy.crash

/**
 * Strips personal data and credentials out of free text before it goes into a
 * crash report. Exception messages are the risky part: a failed request can echo a
 * URL with an email in its query string, and an auth failure can quote a token.
 */
object PiiScrubber {

    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val bearer = Regex("""(?i)bearer\s+[A-Za-z0-9._~+/=-]+""")
    private val jwt = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*""")
    private val queryString = Regex("""(https?://[^\s?#]+)\?[^\s#]*""")

    fun scrub(text: String): String = text
        .replace(jwt, "[jwt]")
        .replace(bearer, "Bearer [token]")
        .replace(queryString) { "${it.groupValues[1]}?[query]" }
        .replace(email, "[email]")
}
