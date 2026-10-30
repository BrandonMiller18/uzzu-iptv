package com.bmiller.uzzu.ui.player

import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.Section
import com.bmiller.uzzu.player.groupKeys
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OverlayModelTest {
    @Before fun setUp() = UiFixtures.pinLocale()

    private val playlist = UiFixtures.playlist(extras = emptyList())

    @Test fun `game tabs hide empty leagues and open on the current group`() {
        val tabs = overlayTabs(playlist, Section.GAMES)
        assertEquals(listOf("All", "MLB", "NHL", "NFL"), tabs)
        val nhl = groupKeys(playlist, Section.GAMES, "NHL")
        assertEquals("NHL", initialOverlayTab(tabs, PlayContext(Section.GAMES, "NHL", nhl, nhl[1], FeedSide.HOME)))
        assertEquals("All", initialOverlayTab(tabs, PlayContext(Section.GAMES, "NBA", nhl, nhl[1], FeedSide.HOME)))
        assertEquals("NBA" in tabs, false)
    }

    @Test fun `group list has every non-empty tab with its item count`() {
        val groups = overlayGroups(playlist, Section.CHANNELS)
        assertEquals(overlayTabs(playlist, Section.CHANNELS), groups.map { it.name })
        groups.forEach { assertEquals(groupKeys(playlist, Section.CHANNELS, it.name).size, it.count) }
        assertTrue(groups.all { it.count > 0 })
    }

    @Test fun `switching list lands on the playing group when it is there, else the first tab`() {
        val nhl = groupKeys(playlist, Section.GAMES, "NHL")
        val game = PlayContext(Section.GAMES, "NHL", nhl, nhl[1], FeedSide.HOME)
        val opened = overlayView(playlist, Section.GAMES, game)
        assertEquals(OverlayView(Section.GAMES, "NHL"), opened)
        // The listed section again: nothing changes (not even the group).
        val mlb = OverlayView(Section.GAMES, "MLB")
        assertEquals(mlb, switchSection(playlist, mlb, game, Section.GAMES))
        // Games → Channels: the playing item is not a channel, so the first tab.
        val ch = switchSection(playlist, mlb, game, Section.CHANNELS)
        assertEquals(OverlayView(Section.CHANNELS, "All"), ch)
        // ... and back to Games on the playing group.
        assertEquals(OverlayView(Section.GAMES, "NHL"), switchSection(playlist, ch, game, Section.GAMES))

        // The other section's rows list it, without PLAYING (the item is in the other section).
        val channelRows = overlayRows(playlist, Section.CHANNELS, "All", game).filterIsInstance<OverlayRow.Item>()
        assertEquals(groupKeys(playlist, Section.CHANNELS, "All"), channelRows.map { it.id })
        assertTrue(channelRows.none { it.playing })

        // An empty other section: stays put.
        val noChannels = playlist.copy(channels = emptyList())
        assertEquals(mlb, switchSection(noChannels, mlb, game, Section.CHANNELS))
    }

    @Test fun `H-A row sits directly under the playing game and focus starts on the playing item`() {
        val nhl = groupKeys(playlist, Section.GAMES, "NHL")
        val playing = PlayContext(Section.GAMES, "NHL", nhl, nhl[2], FeedSide.AWAY)
        val rows = overlayRows(playlist, Section.GAMES, "NHL", playing)
        val i = rows.indexOfFirst { it is OverlayRow.Item && it.playing }
        assertEquals(nhl[2], (rows[i] as OverlayRow.Item).id)
        assertEquals(OverlayRow.Toggle(nhl[2], FeedSide.AWAY), rows[i + 1])
        assertEquals(1, rows.count { it is OverlayRow.Toggle })
        assertEquals("item:${nhl[2]}", overlayFocusKey(rows))

        // Another tab without the playing game: no PLAYING, no toggle, focus on the first row.
        val mlb = overlayRows(playlist, Section.GAMES, "MLB", playing)
        assertFalse(mlb.any { it is OverlayRow.Toggle || (it is OverlayRow.Item && it.playing) })
        assertEquals(mlb.first().key, overlayFocusKey(mlb))
    }

    @Test fun `channel rows carry index and sub-group, never a score`() {
        val keys = groupKeys(playlist, Section.CHANNELS, "League Networks")
        val rows = overlayRows(playlist, Section.CHANNELS, "League Networks", PlayContext(Section.CHANNELS, "League Networks", keys, keys[1]))
        val items = rows.filterIsInstance<OverlayRow.Item>()
        assertEquals("01 · LEAGUE NETWORKS", items[0].detail)
        assertTrue(items[1].playing)
        val games = overlayRows(playlist, Section.GAMES, "All", null).filterIsInstance<OverlayRow.Item>()
        val score = Regex("""\b\d{1,3}\s*[-–]\s*\d{1,3}\b""")
        assertTrue(games.none { score.containsMatchIn(it.detail) || score.containsMatchIn(it.title) })
        assertTrue(games.any { it.live } && games.any { it.final })
        assertTrue(playlist.games.any { it.league == League.NHL })
    }
}
