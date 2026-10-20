package com.bmiller.uzzu.log

/**
 * Masks secrets in any text before it is logged or shown. The playlist URL and every stream URL
 * carry the uzzu account id; the playlist URL also carries the user's email; Fastly away-feed URLs
 * carry an expiry~hash token.
 *
 * Rules (applied to every whitespace-separated token that looks like a URL or path):
 * - URL userinfo (`user:pass@`) → `***@`
 * - Fastly-style `expiry~hash` path segment → `****~****`
 * - any run of 7+ digits (the account id) → `******` + last 4 digits
 * - query string values → `***`
 * Everywhere: email addresses (also `%40`-encoded) → `***@***`.
 */
object SecretMasker {
    private val token = Regex("""\S+""")
    private val userInfo = Regex("""([A-Za-z][A-Za-z0-9+.-]*://)[^/\s@?#]+@""")
    private val tildeToken = Regex("""(?<=/)[^/\s~?#]+~[^/\s?#]+""")
    private val longDigits = Regex("""\d{7,}""")
    private val queryValue = Regex("""([?&;][^=&#\s]+)=([^&#\s]*)""")
    private val email = Regex("""[A-Za-z0-9._%+-]+(?:@|%40)[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    fun mask(text: String?): String {
        if (text.isNullOrEmpty()) return text.orEmpty()
        val urlsMasked = token.replace(text) { m ->
            val t = m.value
            if ('/' in t || "://" in t) maskUrlish(t) else t
        }
        return email.replace(urlsMasked, "***@***")
    }

    private fun maskUrlish(t: String): String {
        var s = userInfo.replace(t) { "${it.groupValues[1]}***@" }
        s = tildeToken.replace(s, "****~****")
        s = longDigits.replace(s) { "******" + it.value.takeLast(4) }
        s = queryValue.replace(s) { "${it.groupValues[1]}=***" }
        return s
    }
}
