package com.bmiller.uzzu.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    private fun v(s: String) = Version.parse(s)!!

    @Test fun `tag with leading v is newer than an older plain version`() {
        assertTrue(Version.isNewer("v1.0.0", "0.9.0"))
        assertFalse(Version.isNewer("v0.9.0", "1.0.0"))
    }

    @Test fun `components compare numerically, not lexically`() {
        assertTrue(Version.isNewer("1.10.0", "1.9.0"))
        assertTrue(Version.isNewer("v2.0.0", "1.99.99"))
        assertTrue(Version.isNewer("1.0.10", "1.0.9"))
    }

    @Test fun `equal versions are not an update`() {
        assertFalse(Version.isNewer("v1.0.0", "1.0.0"))
        assertFalse(Version.isNewer("1.0", "1.0.0"))
        assertEquals(0, v("1.0").compareTo(v("1.0.0")))
        assertFalse(Version.isNewer("v1.0.0+build.7", "1.0.0"))
    }

    @Test fun `a release ranks above its prereleases`() {
        assertTrue(Version.isNewer("v1.0.0", "1.0.0-beta.2"))
        assertFalse(Version.isNewer("v1.0.0-rc.1", "1.0.0"))
        assertTrue(Version.isNewer("v1.0.0-rc.1", "0.9.0"))
    }

    @Test fun `prerelease identifiers follow semver precedence`() {
        val ordered = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0")
        assertEquals(ordered, ordered.shuffled(java.util.Random(7)).sortedWith { a, b -> v(a).compareTo(v(b)) })
    }

    @Test fun `garbage never triggers an update`() {
        assertNull(Version.parse("latest"))
        assertNull(Version.parse(""))
        assertFalse(Version.isNewer("nightly", "1.0.0"))
        // An unparseable current version (should not happen) accepts any valid release.
        assertTrue(Version.isNewer("v1.0.0", "dev"))
    }
}
