package com.bmiller.uzzu.playlist

import java.text.Normalizer

/**
 * Normalizes a team/channel name for keying and lookup (PLAN "Step 1: normalize names"):
 * accents folded ("Montréal" → "montreal"), lowercased, apostrophes dropped ("A's" → "as"), every
 * other punctuation run turned into a space, whitespace collapsed. So "St.Louis Blues",
 * "St. Louis Blues" and "st louis  blues" all become "st louis blues". Aliases (Saint Louis, Utah
 * Hockey Club, ...) live in teams.json, not here.
 */
fun normalizeName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase()
        .replace(apostrophes, "")
        .replace(nonAlphanumeric, " ")
        .trim()
        .replace(whitespace, " ")

private val combiningMarks = Regex("""\p{M}+""")
private val apostrophes = Regex("['’`]")
private val nonAlphanumeric = Regex("""[^\p{L}\p{Nd}]+""")
private val whitespace = Regex("""\s+""")
