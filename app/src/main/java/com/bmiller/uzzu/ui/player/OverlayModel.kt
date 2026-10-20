package com.bmiller.uzzu.ui.player

import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.Section
import com.bmiller.uzzu.player.groupKeys
import com.bmiller.uzzu.player.sectionTabs
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.Playlist
import com.bmiller.uzzu.ui.detailText
import java.time.ZoneId

/** One row of the O1 list. */
sealed interface OverlayRow {
    val key: String

    data class Item(
        val id: String,
        val title: String,
        /** Time / live period / FINAL for games; `07 · REGIONAL SPORTS` for channels. Never a score. */
        val detail: String,
        val live: Boolean,
        val final: Boolean,
        val playing: Boolean,
    ) : OverlayRow {
        override val key get() = "item:$id"
    }

    /** Home/Away toggle, directly under the playing game (only when it has both feeds). */
    data class Toggle(val gameId: String, val side: FeedSide) : OverlayRow {
        override val key get() = KEY_TOGGLE
    }

    companion object {
        const val KEY_TOGGLE = "toggle"
        const val KEY_EXIT = "exit"
    }
}

/** Tab the overlay opens on: the current group, else the first tab. */
fun initialOverlayTab(tabs: List<String>, ctx: PlayContext?): String? =
    ctx?.group?.takeIf { it in tabs } ?: tabs.firstOrNull()

/** Left/Right: neighbour tab, clamped at the ends. */
fun neighbourTab(tabs: List<String>, current: String, delta: Int): String {
    if (tabs.isEmpty()) return current
    val i = tabs.indexOf(current).coerceAtLeast(0)
    return tabs[(i + delta).coerceIn(0, tabs.lastIndex)]
}

fun overlayTabs(playlist: Playlist, section: Section) = sectionTabs(playlist, section)

/** Rows of [tab]: items, plus the H/A row under the playing game. */
fun overlayRows(playlist: Playlist, section: Section, tab: String, playing: PlayContext?, zone: ZoneId = ZoneId.systemDefault()): List<OverlayRow> {
    val keys = groupKeys(playlist, section, tab)
    val playingKey = playing?.currentKey?.takeIf { playing.section == section }
    return when (section) {
        Section.GAMES -> {
            val byId = playlist.games.associateBy { it.id }
            keys.mapNotNull(byId::get).flatMap { g ->
                val isPlaying = g.id == playingKey
                val item = OverlayRow.Item(g.id, g.title, g.detailText(zone), g.status is GameStatus.Live, g.status == GameStatus.Final, isPlaying)
                if (isPlaying && g.homeFeed != null && g.awayFeed != null) listOf(item, OverlayRow.Toggle(g.id, playing.side ?: FeedSide.HOME)) else listOf(item)
            }
        }
        Section.CHANNELS -> {
            val byKey = playlist.channels.associateBy { it.key }
            keys.mapIndexedNotNull { i, k ->
                byKey[k]?.let { ch -> OverlayRow.Item(ch.key, ch.name, "%02d · %s".format(i + 1, ch.subGroup.label.uppercase()), false, false, ch.key == playingKey) }
            }
        }
    }
}

/** Focus on open / tab switch: the playing item if it is in the tab, else the first row. */
fun overlayFocusKey(rows: List<OverlayRow>): String =
    rows.firstOrNull { it is OverlayRow.Item && it.playing }?.key ?: rows.firstOrNull()?.key ?: OverlayRow.KEY_EXIT
