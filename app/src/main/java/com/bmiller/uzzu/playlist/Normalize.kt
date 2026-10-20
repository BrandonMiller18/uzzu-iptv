package com.bmiller.uzzu.playlist

/**
 * Normalizes a team/channel name for keying and lookup: lowercase, punctuation stripped, whitespace
 * collapsed. "St.Louis Blues" → "st louis blues". M2 extends this with alias/quirk handling.
 */
fun normalizeName(name: String): String =
    name.lowercase()
        .replace(apostrophes, "")
        .replace(nonAlphanumeric, " ")
        .trim()
        .replace(whitespace, " ")

private val apostrophes = Regex("['’`]")
private val nonAlphanumeric = Regex("""[^\p{L}\p{Nd}]+""")
private val whitespace = Regex("""\s+""")
