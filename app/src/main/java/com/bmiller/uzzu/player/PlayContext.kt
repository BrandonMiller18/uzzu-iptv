package com.bmiller.uzzu.player

import com.bmiller.uzzu.playlist.FeedSide

enum class Section { GAMES, CHANNELS }

/**
 * Where a tune came from (PLAN "Navigation rules"). [group] is the tab the user played from (e.g.
 * "All", "NHL", "Regional Sports"); [itemKeys] is that tab's ordered items (Game.id / Channel.id),
 * so CH up/down (M4) can cycle within it and re-resolve items against a refreshed playlist.
 * [side] is the feed side last chosen for a game (null for channels).
 */
data class PlayContext(
    val section: Section,
    val group: String,
    val itemKeys: List<String>,
    val currentKey: String,
    val side: FeedSide? = null,
)
