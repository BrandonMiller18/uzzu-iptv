package com.bmiller.uzzu.player

import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.playlist.M3uParser
import com.bmiller.uzzu.playlist.Playlist
import com.bmiller.uzzu.schedule.Fixtures
import com.bmiller.uzzu.ui.player.overlayRows
import com.bmiller.uzzu.ui.player.OverlayRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Fixture + a home-only NBA game, an away-only NBA game and an unpaired title. */
    private val playlist: Playlist = Fixtures.playlist(
        listOf("Boston Celtics @ Detroit Pistons-H", "Miami Heat @ Orlando Magic-A", "UFC 300 Main Card"),
    )
    private val sched = FakeScheduler()
    private val player = FakeStreamPlayer()
    private val source = FakePlaylistSource(playlist, sched.now).also { it.clock = sched.clock }
    private val logger by lazy { AppLogger(tmp.newFolder("logs")) }
    private val manager by lazy {
        PlaybackManager(player, source, source.state, { "Wi-Fi · Connected" }, logger, CoroutineScope(UnconfinedTestDispatcher()), sched, sched.clock)
    }

    private fun group(section: Section, tab: String) = groupKeys(playlist, section, tab)
    private fun ctx(section: Section, tab: String, key: String, side: FeedSide? = null) =
        PlayContext(section, tab, group(section, tab), key, side)
    private fun now() = manager.state.value.now!!.context

    @Test fun `CH up and down wrap within the current group`() {
        val nhl = group(Section.GAMES, "NHL")
        assertEquals(4, nhl.size)
        manager.tune(ctx(Section.GAMES, "NHL", nhl.last(), FeedSide.HOME))
        manager.step(1)
        assertEquals(nhl.first(), now().currentKey)
        assertEquals("NHL", now().group)
        manager.step(-1)
        assertEquals(nhl.last(), now().currentKey)
        manager.step(-1)
        assertEquals(nhl[2], now().currentKey)

        val league = group(Section.CHANNELS, "League Networks")
        manager.tune(ctx(Section.CHANNELS, "League Networks", league.first()))
        manager.step(-1)
        assertEquals(league.last(), now().currentKey)
        assertEquals(playlist.channels.first { it.key == league.last() }.url, player.loads.last())
    }

    @Test fun `CH keeps the chosen side, falls back on single-feed games, and returns to the choice`() {
        val nba = group(Section.GAMES, "NBA")
        val homeOnly = playlist.games.single { it.awayFeed == null && it.league == League.NBA }.id
        val awayOnly = playlist.games.single { it.homeFeed == null }.id
        assertEquals(setOf(homeOnly, awayOnly), nba.toSet())
        manager.tune(ctx(Section.GAMES, "NBA", awayOnly, FeedSide.AWAY))
        manager.step(if (nba.indexOf(homeOnly) > nba.indexOf(awayOnly)) 1 else -1)
        assertEquals(homeOnly, now().currentKey)
        assertEquals(FeedSide.HOME, now().side)
        manager.step(1)
        assertEquals(awayOnly, now().currentKey)
        assertEquals(FeedSide.AWAY, now().side)

        val nhl = group(Section.GAMES, "NHL")
        manager.tune(ctx(Section.GAMES, "NHL", nhl[0], FeedSide.AWAY))
        manager.step(1)
        assertEquals(FeedSide.AWAY, now().side)
        assertEquals(playlist.games.first { it.id == nhl[1] }.awayFeed!!.url, player.loads.last())
    }

    @Test fun `Back toggles a single last-channel slot including the feed side`() {
        assertFalse(manager.toggleLast())
        val nhl = group(Section.GAMES, "NHL")
        manager.tune(ctx(Section.GAMES, "NHL", nhl[0], FeedSide.HOME))
        assertFalse(manager.toggleLast())
        manager.flipSide()
        assertEquals(FeedSide.AWAY, now().side)
        assertEquals(FeedSide.HOME, manager.state.value.last!!.side)
        val ch = group(Section.CHANNELS, "All")
        manager.tune(ctx(Section.CHANNELS, "All", ch[3]))
        assertEquals(nhl[0] to FeedSide.AWAY, manager.state.value.last!!.let { it.currentKey to it.side })
        assertTrue(manager.toggleLast())
        assertEquals(nhl[0], now().currentKey)
        assertEquals(FeedSide.AWAY, now().side)
        assertEquals("NHL", now().group)
        assertEquals(ch[3], manager.state.value.last!!.currentKey)
        assertTrue(manager.toggleLast())
        assertEquals(ch[3], now().currentKey)
        // Re-tuning what is playing keeps the slot.
        manager.tune(ctx(Section.CHANNELS, "All", ch[3]))
        assertEquals(nhl[0], manager.state.value.last!!.currentKey)
        manager.stop()
        assertNull(manager.state.value.last)
    }

    @Test fun `Back clears the last-channel slot and returns false when that item left the playlist`() {
        val nhl = group(Section.GAMES, "NHL")
        val ch = group(Section.CHANNELS, "All")
        manager.tune(ctx(Section.GAMES, "NHL", nhl[0], FeedSide.HOME))
        manager.tune(ctx(Section.CHANNELS, "All", ch[0]))
        assertEquals(nhl[0], manager.state.value.last!!.currentKey)
        // The game ended and dropped out on a refresh.
        source.state.value = com.bmiller.uzzu.data.PlaylistState.Loaded(playlist.copy(games = playlist.games.filter { it.id != nhl[0] }), sched.now)
        val loads = player.loads.size
        assertFalse(manager.toggleLast())
        assertNull(manager.state.value.last)
        assertEquals(ch[0], now().currentKey)
        assertEquals(loads, player.loads.size)
        assertFalse(manager.toggleLast())
    }

    @Test fun `CH from an item that left its group continues from its old position`() {
        val league = group(Section.CHANNELS, "League Networks")
        manager.tune(ctx(Section.CHANNELS, "League Networks", league[1]))
        val gone = league[1]
        source.state.value = com.bmiller.uzzu.data.PlaylistState.Loaded(playlist.copy(channels = playlist.channels.filter { it.key != gone }), sched.now)
        manager.step(1)
        assertEquals(league[2], now().currentKey)
    }

    @Test fun `a policy reload ends a pause and pausing stops the stall timer`() {
        val ch = group(Section.CHANNELS, "All")
        manager.tune(ctx(Section.CHANNELS, "All", ch[0]))
        player.listener!!.onBuffering()
        manager.togglePause()
        assertTrue(manager.state.value.paused)
        sched.advance(60_000) // no false STALL while paused
        assertEquals(1, player.loads.size)
        // An error while paused: the backoff reload plays again, so the paused tag clears.
        player.fail(500)
        sched.advance(2_000)
        assertEquals(2, player.loads.size)
        assertFalse(manager.state.value.paused)
    }

    @Test fun `onStop stops the policy and onStart re-resolves and reloads the item`() {
        val ch = group(Section.CHANNELS, "All")
        manager.tune(ctx(Section.CHANNELS, "All", ch[0]))
        player.listener!!.onBuffering()
        player.fail(500) // backoff pending
        manager.onBackground()
        sched.advance(120_000)
        assertEquals(1, player.loads.size)
        assertNull(manager.state.value.error)
        // The token rotated while stopped.
        val newUrl = "https://uzzu.tv/hls/10000007220/rotated.m3u8"
        source.state.value = com.bmiller.uzzu.data.PlaylistState.Loaded(
            playlist.copy(channels = playlist.channels.map { if (it.key == ch[0]) it.copy(url = newUrl) else it }), sched.now,
        )
        manager.togglePause()
        manager.onForeground()
        assertEquals(newUrl, player.loads.last())
        assertFalse(manager.state.value.paused)
        assertEquals(ch[0], now().currentKey)
        // A fresh budget: two retries before S6.
        player.fail(500); sched.advance(2_000)
        player.fail(500); sched.advance(5_000)
        assertNull(manager.state.value.error)
    }

    @Test fun `onStart while S6 is up does not reload, nothing playing does nothing`() {
        manager.onForeground()
        assertTrue(player.loads.isEmpty())
        val ch = group(Section.CHANNELS, "All")
        manager.tune(ctx(Section.CHANNELS, "All", ch[0]))
        player.fail(404); sched.advance(2_000); player.fail(404); sched.advance(5_000); player.fail(404)
        assertNotNull(manager.state.value.error)
        val loads = player.loads.size
        manager.onBackground()
        manager.onForeground()
        assertEquals(loads, player.loads.size)
    }

    @Test fun `setGroup on the playing item swaps the CH group without reloading`() {
        val all = group(Section.CHANNELS, "All")
        val league = group(Section.CHANNELS, "League Networks")
        val key = league[0]
        manager.tune(ctx(Section.CHANNELS, "All", key))
        val loads = player.loads.size
        val tunes = manager.state.value.tuneCount
        manager.setGroup(ctx(Section.CHANNELS, "League Networks", key))
        assertEquals(loads, player.loads.size)
        assertEquals(tunes, manager.state.value.tuneCount)
        assertEquals("League Networks", now().group)
        assertEquals(league, now().itemKeys)
        manager.step(1)
        assertEquals(league[1], now().currentKey)
        assertTrue(all.size > league.size)
    }

    @Test fun `merge rule - a refresh never touches the playing stream, the new URL is used on the next tune`() {
        val ch = group(Section.CHANNELS, "All")
        manager.tune(ctx(Section.CHANNELS, "All", ch[0]))
        val oldUrl = player.loads.single()
        val changed = playlist.copy(channels = playlist.channels.map { if (it.key == ch[0]) it.copy(url = it.url.replace(".m3u8", "-new.m3u8")) else it })
        source.next = { changed }
        sched.advance(11 * 60_000L)
        manager.overlayOpened() // silent refresh (stale)
        assertEquals(1, source.refreshes)
        assertEquals(listOf(oldUrl), player.loads)
        assertEquals(ch[0], now().currentKey)
        manager.step(1)
        manager.toggleLast()
        assertEquals(oldUrl.replace(".m3u8", "-new.m3u8"), player.loads.last())
    }

    @Test fun `overlay open refreshes only a playlist older than 10 minutes`() {
        manager.tune(ctx(Section.CHANNELS, "All", group(Section.CHANNELS, "All")[0]))
        sched.advance(9 * 60_000L)
        manager.overlayOpened()
        assertEquals(0, source.refreshes)
        sched.advance(2 * 60_000L)
        manager.overlayOpened()
        assertEquals(1, source.refreshes)
        assertEquals(1, player.loads.size)
    }

    @Test fun `expired away URL recovers through a playlist re-fetch without user action`() {
        val game = playlist.games.first { it.awayFeed?.url?.contains("fastly.net") == true }
        val fresh = game.awayFeed!!.url.replace("1792476833", "1799999999")
        source.next = { playlist.copy(games = playlist.games.map { if (it.id == game.id) it.copy(awayFeed = it.awayFeed!!.copy(url = fresh)) else it }) }
        manager.tune(ctx(Section.GAMES, "All", game.id, FeedSide.AWAY))
        player.fail(403, host = "tran03.global.ssl.fastly.net")
        assertEquals(listOf(game.awayFeed!!.url, fresh), player.loads)
        player.listener!!.onFirstFrame()
        sched.advance(60_000)
        assertNull(manager.state.value.error)
        assertEquals(1, source.refreshes)
    }

    @Test fun `exhausted policy shows S6 with status, error code, masked URL and attempts`() {
        val ch = playlist.channels.first()
        manager.tune(ctx(Section.CHANNELS, "All", ch.key))
        player.fail(404, host = "uzzu.tv")
        sched.advance(2_000)
        player.fail(404, host = "uzzu.tv")
        sched.advance(5_000)
        player.fail(404, host = "uzzu.tv")
        val report = assertNotNull(manager.state.value.error).let { manager.state.value.error!! }
        assertEquals(404, report.httpStatus)
        assertEquals("ERROR_CODE_IO_BAD_HTTP_STATUS", report.errorCodeName)
        assertEquals("uzzu.tv /hls/******7220/1.m3u8", report.maskedStream)
        assertEquals("Stream returned 404", report.summary)
        val lines = report.lines.associate { it.label to it }
        assertTrue(lines.getValue("HTTP_STATUS").failing)
        assertTrue(lines.getValue("PLAYER_ERR").failing)
        assertTrue(lines.getValue("ATTEMPTS").value.startsWith("3 · "))
        assertEquals("Wi-Fi · Connected", lines.getValue("NETWORK").value)
        assertTrue(lines.getValue("PLAYLIST").value.startsWith("Age 0 min · Last refresh"))
        assertFalse(report.lines.any { "10000007220" in it.value })
        assertEquals(1, player.stops)
        // Logged, masked.
        val log = logger.file.readText()
        assertTrue("error screen: Stream returned 404" in log)
        assertFalse("10000007220" in log)

        // Retry re-runs the full policy.
        manager.retry()
        assertNull(manager.state.value.error)
        assertEquals(4, player.loads.size)
        player.fail(404)
        sched.advance(2_000)
        assertEquals(5, player.loads.size)
    }

    @Test fun `play-pause pauses and resumes at the live edge`() {
        manager.tune(ctx(Section.CHANNELS, "All", playlist.channels.first().key))
        manager.togglePause()
        assertTrue(player.paused)
        assertTrue(manager.state.value.paused)
        manager.togglePause()
        assertEquals(1, player.liveEdgeResumes)
        assertFalse(manager.state.value.paused)
    }

    @Test fun `duplicate channel ids get unique keys that drive tune, CH and the overlay`() {
        val m3u = """
            #EXTM3U
            #EXTINF:0 tvg-id="ESPN" tvg-logo="" group-title="Live-Channels",ESPN
            https://uzzu.tv/hls/10000007220/10.m3u8
            #EXTINF:0 tvg-id="ESPN" tvg-logo="" group-title="Live-Channels",ESPN
            https://uzzu.tv/hls/10000007220/11.m3u8
            #EXTINF:0 tvg-id="" tvg-logo="" group-title="Live-Channels",Fox News
            https://uzzu.tv/hls/10000007220/12.m3u8
        """.trimIndent()
        val p = Fixtures.classifier.classify(M3uParser().parse(m3u))
        assertEquals(listOf("ESPN", "ESPN#2", "Fox News"), p.channels.map { it.key })
        source.state.value = com.bmiller.uzzu.data.PlaylistState.Loaded(p, sched.now)
        val keys = groupKeys(p, Section.CHANNELS, "National Sports")
        assertEquals(listOf("ESPN", "ESPN#2"), keys)
        manager.tune(PlayContext(Section.CHANNELS, "National Sports", keys, "ESPN#2"))
        assertEquals("https://uzzu.tv/hls/10000007220/11.m3u8", player.loads.last())
        manager.step(1)
        assertEquals("ESPN", now().currentKey)
        assertEquals("https://uzzu.tv/hls/10000007220/10.m3u8", player.loads.last())
        manager.toggleLast()
        val rows = overlayRows(p, Section.CHANNELS, "National Sports", now()).filterIsInstance<OverlayRow.Item>()
        assertEquals(listOf(false, true), rows.map { it.playing })
        // Keys survive a refresh (same playlist order).
        val refreshed = Fixtures.classifier.classify(M3uParser().parse(m3u))
        assertEquals(p.channels.map { it.key }, refreshed.channels.map { it.key })
    }
}
