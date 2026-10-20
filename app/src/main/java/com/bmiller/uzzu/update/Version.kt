package com.bmiller.uzzu.update

/**
 * Semantic version (`v1.2.3`, `1.2.3-beta.1`, `1.2`), compared per semver 2.0: numeric core, then a
 * release ranks above its prereleases, prerelease identifiers compare numerically when both are
 * numbers, otherwise lexically. Build metadata (`+...`) is ignored. Unparseable input → null.
 */
data class Version(val core: List<Int>, val prerelease: List<String>) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        for (i in 0 until maxOf(core.size, other.core.size)) {
            val c = core.getOrElse(i) { 0 }.compareTo(other.core.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return other.prerelease.size.coerceAtMost(1) - prerelease.size.coerceAtMost(1)
        }
        for (i in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val c = compareIdentifier(prerelease[i], other.prerelease[i])
            if (c != 0) return c
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    override fun toString(): String =
        core.joinToString(".") + if (prerelease.isEmpty()) "" else "-" + prerelease.joinToString(".")

    companion object {
        fun parse(text: String): Version? {
            val s = text.trim().removePrefix("v").removePrefix("V").substringBefore('+')
            val coreText = s.substringBefore('-')
            val core = coreText.split('.').map { it.toIntOrNull() ?: return null }
            if (core.isEmpty() || core.size > 4) return null
            val pre = if ('-' in s) s.substringAfter('-').split('.').filter { it.isNotEmpty() } else emptyList()
            return Version(core, pre)
        }

        /** True when [candidate] (a release tag) is strictly newer than [current] (VERSION_NAME). */
        fun isNewer(candidate: String, current: String): Boolean {
            val c = parse(candidate) ?: return false
            val cur = parse(current) ?: return true
            return c > cur
        }

        private fun compareIdentifier(a: String, b: String): Int {
            val x = a.toLongOrNull()
            val y = b.toLongOrNull()
            return when {
                x != null && y != null -> x.compareTo(y)
                x != null -> -1 // numeric identifiers rank below alphanumeric ones
                y != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
