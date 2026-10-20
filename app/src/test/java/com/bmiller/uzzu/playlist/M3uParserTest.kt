package com.bmiller.uzzu.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val logs = mutableListOf<String>()
    private val parser = M3uParser { logs += it }

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader!!.getResource("fixtures/sample.m3u")).readText()

    private fun extinf(name: String, group: String, logo: String? = null, id: String? = name) = buildString {
        append("#EXTINF:0")
        if (id != null) append(" tvg-id=\"$id\"")
        if (logo != null) append(" tvg-logo=\"$logo\"")
        append(" group-title=\"$group\",$name")
    }

    // ---- scrubbed fixture ----

    @Test
    fun `fixture yields 6 paired games and 96 channels`() {
        val p = parser.parse(fixture())
        assertEquals(6, p.games.size)
        assertEquals(96, p.channels.size)
        assertTrue(p.games.all { it.homeFeed != null && it.awayFeed != null })
        assertEquals(10, p.stats.ignoredReplays)
        assertEquals(0, p.stats.malformed)
        assertEquals(0, p.stats.duplicates)
        assertEquals(0, p.stats.ignoredUnknownGroup)
    }

    @Test
    fun `fixture games keep playlist order and split team names`() {
        val games = parser.parse(fixture()).games
        assertEquals(
            listOf(
                "Anaheim Ducks @ New York Rangers",
                "Colorado Avalanche @ Philadelphia Flyers",
                "San Jose Sharks @ Toronto Maple Leafs",
                "Cleveland Guardians @ Tampa Bay Rays",
                "Washington Commanders @ San Francisco 49ers",
                "Pittsburgh Penguins @ Utah Mammoth",
            ),
            games.map { it.title },
        )
        assertEquals(games.sortedBy { it.playlistIndex }, games)
        val first = games.first()
        assertEquals("Anaheim Ducks", first.awayName)
        assertEquals("New York Rangers", first.homeName)
        assertEquals("anaheim ducks|new york rangers", first.id)
        assertEquals(League.UNGROUPED, first.league)
        assertEquals(GameStatus.Unknown, first.status)
        assertNull(first.startTime)
        assertNull(first.rawTitle)
    }

    @Test
    fun `home and away feeds carry their own URL and team logo`() {
        val g = parser.parse(fixture()).games.first()
        val home = requireNotNull(g.homeFeed)
        val away = requireNotNull(g.awayFeed)
        assertEquals(FeedSide.HOME, home.side)
        assertEquals(FeedSide.AWAY, away.side)
        assertEquals("https://uzzu.tv/hls/10000007220/g4678.m3u8", home.url)
        assertTrue(away.url.startsWith("https://tran03.global.ssl.fastly.net/1792476833~"))
        assertEquals("https://uzzu.tv/hls/10000007220/logo/t81", g.homeLogoUrl)
        assertEquals("https://uzzu.tv/hls/10000007220/logo/t63", g.awayLogoUrl)

        val utah = parser.parse(fixture()).games.last()
        assertEquals("https://uzzu.tv/hls/10000007220/logo/t519", utah.homeLogoUrl)
        assertEquals("https://uzzu.tv/hls/10000007220/logo/t84", utah.awayLogoUrl)
    }

    @Test
    fun `channels keep playlist order with attributes`() {
        val channels = parser.parse(fixture()).channels
        assertEquals(listOf("NFL Network", "Red Zone", "Red Zone2"), channels.take(3).map { it.name })
        val nfl = channels.first()
        assertEquals("NFL Network", nfl.id)
        assertEquals("https://uzzu.tv/hls/10000007220/1.m3u8", nfl.url)
        assertEquals("https://uzzu.tv/hls/10000007220/logo/1", nfl.logoUrl)
        assertEquals(ChannelGroup.OTHER, nfl.subGroup)
        assertEquals(channels.sortedBy { it.playlistIndex }, channels)
        assertTrue(channels.map { it.playlistIndex }.zipWithNext().all { (a, b) -> a < b })
    }

    // ---- inline edge cases ----

    @Test
    fun `away feed listed before home feed still pairs`() {
        val text = listOf(
            "#EXTM3U",
            extinf("A Team @ B Team-A", "Live-Games", "https://x/logo/a"), "https://cdn.example/a.m3u8",
            extinf("A Team @ B Team-H", "Live-Games", "https://x/logo/b"), "https://cdn.example/h.m3u8",
        ).joinToString("\n")
        val g = parser.parse(text).games.single()
        assertEquals("https://cdn.example/h.m3u8", g.homeFeed?.url)
        assertEquals("https://cdn.example/a.m3u8", g.awayFeed?.url)
        assertEquals("https://x/logo/b", g.homeLogoUrl)
        assertEquals("https://x/logo/a", g.awayLogoUrl)
        assertEquals(0, g.playlistIndex)
    }

    @Test
    fun `feeds pair by normalized names`() {
        val text = listOf(
            extinf("St.Louis Blues @ Dallas Stars-H", "Live-Games"), "https://h/1.m3u8",
            extinf("St. Louis Blues @ Dallas  Stars-A", "Live-Games"), "https://a/1.m3u8",
        ).joinToString("\n")
        val g = parser.parse(text).games.single()
        assertEquals("st louis blues|dallas stars", g.id)
        assertNotNull(g.homeFeed)
        assertNotNull(g.awayFeed)
    }

    @Test
    fun `single feed game keeps the other side null`() {
        val text = extinf("A @ B-H", "Live-Games") + "\nhttps://h/1.m3u8"
        val g = parser.parse(text).games.single()
        assertNotNull(g.homeFeed)
        assertNull(g.awayFeed)
    }

    @Test
    fun `regex miss becomes single feed ungrouped game with raw title`() {
        val text = listOf(
            extinf("Big Fight Night", "Live-Games", "https://x/logo/f"), "https://h/fight.m3u8",
            extinf("A @ B-X", "Live-Games"), "https://h/x.m3u8",
        ).joinToString("\n")
        val games = parser.parse(text).games
        assertEquals(2, games.size)
        val fight = games[0]
        assertEquals("Big Fight Night", fight.title)
        assertEquals("Big Fight Night", fight.rawTitle)
        assertEquals(League.UNGROUPED, fight.league)
        assertEquals("https://h/fight.m3u8", fight.homeFeed?.url)
        assertNull(fight.awayFeed)
        assertEquals("A @ B-X", games[1].title)
    }

    @Test
    fun `tolerates CRLF, BOM and blank lines`() {
        val bom = Char(0xFEFF).toString()
        val text = bom + listOf(
            "#EXTM3U", "", "   ",
            extinf("A @ B-H", "Live-Games"), "", "https://h/1.m3u8", "",
            extinf("Chan 1", "Live-Channels"), "https://h/c1.m3u8", "",
        ).joinToString("\r\n")
        val p = parser.parse(text)
        assertEquals(1, p.games.size)
        assertEquals("https://h/1.m3u8", p.games[0].homeFeed?.url)
        assertEquals(listOf("Chan 1"), p.channels.map { it.name })
        assertEquals("https://h/c1.m3u8", p.channels[0].url)
        assertEquals(0, p.stats.malformed)
    }

    @Test
    fun `tolerates bare CR line endings`() {
        val text = listOf("#EXTM3U", extinf("Chan 1", "Live-Channels"), "https://h/c1.m3u8").joinToString("\r")
        assertEquals(1, parser.parse(text).channels.size)
    }

    @Test
    fun `missing attributes are tolerated`() {
        val text = listOf(
            "#EXTINF:0 group-title=\"Live-Channels\",No Logo Channel", "https://h/c1.m3u8",
            "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"\" group-title=\"Live-Channels\",Empty Attrs", "https://h/c2.m3u8",
        ).joinToString("\n")
        val ch = parser.parse(text).channels
        assertEquals(2, ch.size)
        assertNull(ch[0].logoUrl)
        assertEquals("No Logo Channel", ch[0].id)
        assertEquals("Empty Attrs", ch[1].id)
        assertNull(ch[1].logoUrl)
    }

    @Test
    fun `entry without group-title is ignored and logged`() {
        val text = "#EXTINF:0 tvg-id=\"x\",Orphan\nhttps://h/o.m3u8"
        val p = parser.parse(text)
        assertTrue(p.games.isEmpty() && p.channels.isEmpty())
        assertEquals(1, p.stats.ignoredUnknownGroup)
        assertTrue(logs.any { "unknown group-title" in it })
    }

    @Test
    fun `unknown group ignored and logged once, replays ignored quietly`() {
        val text = listOf(
            extinf("Movie 1", "Movies"), "https://h/m1.m3u8",
            extinf("Movie 2", "Movies"), "https://h/m2.m3u8",
            extinf("Old Game", "NFL-Replays"), "https://h/r1.m3u8",
            extinf("Older Game", "MLB-Replays"), "https://h/r2.m3u8",
        ).joinToString("\n")
        val p = parser.parse(text)
        assertTrue(p.games.isEmpty() && p.channels.isEmpty())
        assertEquals(2, p.stats.ignoredUnknownGroup)
        assertEquals(2, p.stats.ignoredReplays)
        assertEquals(1, logs.count { "\"Movies\"" in it })
        assertTrue(logs.none { "Replays" in it })
    }

    @Test
    fun `malformed entries are skipped and logged without failing the parse`() {
        val text = listOf(
            "#EXTM3U",
            extinf("No Url", "Live-Channels"), // followed by another EXTINF
            extinf("Good 1", "Live-Channels"), "https://h/g1.m3u8",
            "https://h/orphan.m3u8", // URL without EXTINF
            extinf("Bad Scheme", "Live-Channels"), "ftp://h/bad.m3u8",
            "#EXTINF:0 group-title=\"Live-Channels\"", "https://h/noname.m3u8", // no name at all
            extinf("Good 2", "Live-Channels"), "https://h/g2.m3u8",
            extinf("Trailing", "Live-Channels"), // EXTINF at EOF
        ).joinToString("\n")
        val p = parser.parse(text)
        assertEquals(listOf("Good 1", "Good 2"), p.channels.map { it.name })
        assertEquals(5, p.stats.malformed)
        assertEquals(5, logs.count { "malformed" in it })
    }

    @Test
    fun `garbage input returns an empty playlist`() {
        val p = parser.parse("<html><body>Not found</body></html>")
        assertTrue(p.games.isEmpty() && p.channels.isEmpty())
        assertEquals(1, p.stats.malformed)
        assertTrue(parser.parse("").games.isEmpty())
    }

    @Test
    fun `duplicate entries are dropped`() {
        val text = listOf(
            extinf("Chan 1", "Live-Channels"), "https://h/c1.m3u8",
            extinf("Chan 1", "Live-Channels"), "https://h/c1.m3u8",
            extinf("A @ B-H", "Live-Games"), "https://h/1.m3u8",
            extinf("A @ B-H", "Live-Games"), "https://h/1.m3u8",
            extinf("A @ B-H", "Live-Games"), "https://h/1-other.m3u8", // same side, different URL
            extinf("A @ B-A", "Live-Games"), "https://a/1.m3u8",
        ).joinToString("\n")
        val p = parser.parse(text)
        assertEquals(1, p.channels.size)
        assertEquals(2, p.stats.duplicates)
        val g = p.games.single()
        assertEquals("https://h/1.m3u8", g.homeFeed?.url)
        assertEquals("https://a/1.m3u8", g.awayFeed?.url)
        assertTrue(logs.any { "duplicate home feed" in it })
    }

    @Test
    fun `display name is text after the last top-level comma`() {
        val text = listOf(
            "#EXTINF:0 tvg-id=\"x, y\" tvg-logo=\"https://l/a,b.png\" group-title=\"Live-Channels\",Fox, News",
            "https://h/c.m3u8",
        ).joinToString("\n")
        val ch = parser.parse(text).channels.single()
        assertEquals("News", ch.name)
        assertEquals("x, y", ch.id)
        assertEquals("https://l/a,b.png", ch.logoUrl)
    }

    @Test
    fun `missing display name falls back to tvg-id`() {
        val text = "#EXTINF:0 tvg-id=\"Chan X\" group-title=\"Live-Channels\",\nhttps://h/x.m3u8"
        assertEquals("Chan X", parser.parse(text).channels.single().name)
    }

    @Test
    fun `duplicate channel ids get unique keys in playlist order`() {
        val text = listOf("ESPN" to 1, "ESPN" to 2, "Fox" to 3, "ESPN" to 4).joinToString("\n") { (id, n) ->
            "#EXTINF:0 tvg-id=\"$id\" group-title=\"Live-Channels\",$id\nhttps://h/$n.m3u8"
        }
        val ch = parser.parse(text).channels
        assertEquals(listOf("ESPN", "ESPN", "Fox", "ESPN"), ch.map { it.id })
        assertEquals(listOf("ESPN", "ESPN#2", "Fox", "ESPN#3"), ch.map { it.key })
    }
}
